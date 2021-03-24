package org.moire.ultrasonic.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.RemoteControlClient
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.audiofx.VisualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.fragment.PlayerFragment
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver
import org.moire.ultrasonic.util.*
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Represents a Media Player which uses the mobile's resources for playback
 */
class LocalMediaPlayer(private val audioFocusHandler: AudioFocusHandler, private val context: Context) {
    @JvmField
    var onCurrentPlayingChanged: Consumer<DownloadFile?>? = null
    @JvmField
    var onSongCompleted: Consumer<DownloadFile?>? = null
    @JvmField
    var onPlayerStateChanged: BiConsumer<PlayerState, DownloadFile?>? = null
    @JvmField
    var onPrepared: Runnable? = null
    @JvmField
    var onNextSongRequested: Runnable? = null
    @JvmField
    var playerState = PlayerState.IDLE
    @JvmField
    var currentPlaying: DownloadFile? = null
    @JvmField
    var nextPlaying: DownloadFile? = null
    private var nextPlayerState = PlayerState.IDLE
    private var nextSetup = false
    private var nextPlayingTask: CancellableTask? = null
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var nextMediaPlayer: MediaPlayer? = null
    private var mediaPlayerLooper: Looper? = null
    private var mediaPlayerHandler: Handler? = null
    private var cachedPosition = 0
    private var proxy: StreamProxy? = null
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var remoteControlClient: RemoteControlClient? = null
    private var bufferTask: CancellableTask? = null
    private var positionCache: PositionCache? = null
    private var secondaryProgress = -1
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock: WakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)

    fun onCreate() {
        Thread {
            Thread.currentThread().name = "MediaPlayerThread"
            Looper.prepare()
            mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setOnErrorListener { mediaPlayer, what, more ->
                handleError(Exception(String.format(Locale.getDefault(), "MediaPlayer error: %d (%d)", what, more)))
                false
            }
            try {
                val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.audioSessionId)
                i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                context.sendBroadcast(i)
            } catch (e: Throwable) {
                // Froyo or lower
            }
            mediaPlayerLooper = Looper.myLooper()
            mediaPlayerHandler = Handler(mediaPlayerLooper!!)
            Looper.loop()
        }.start()

        // Create Equalizer and Visualizer on a new thread as this can potentially take some time
        Thread {
            EqualizerController.create(context, mediaPlayer)
            VisualizerController.create(mediaPlayer)
        }.start()

        wakeLock.setReferenceCounted(false)
        Util.registerMediaButtonEventReceiver(context, true)
        setUpRemoteControlClient()
        Timber.i("LocalMediaPlayer created")
    }

    fun onDestroy() {
        reset()
        try {
            val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(i)
            EqualizerController.release()
            VisualizerController.release()
            mediaPlayer.release()
            if (nextMediaPlayer != null) {
                nextMediaPlayer!!.release()
            }
            mediaPlayerLooper!!.quit()
            if (bufferTask != null) {
                bufferTask!!.cancel()
            }
            if (nextPlayingTask != null) {
                nextPlayingTask!!.cancel()
            }
            audioManager.unregisterRemoteControlClient(remoteControlClient)
            clearRemoteControl()
            Util.unregisterMediaButtonEventReceiver(context, true)
            wakeLock.release()
        } catch (exception: Throwable) {
            Timber.w(exception, "LocalMediaPlayer onDestroy exception: ")
        }
        Timber.i("LocalMediaPlayer destroyed")
    }

    @Synchronized
    fun setPlayerState(playerState: PlayerState) {
        Timber.i("%s -> %s (%s)", this.playerState.name, playerState.name, currentPlaying)
        this.playerState = playerState
        if (playerState === PlayerState.STARTED) {
            audioFocusHandler.requestAudioFocus()
        }
        if (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED) {
            updateRemoteControl()
        }
        if (onPlayerStateChanged != null) {
            val mainHandler = Handler(context.mainLooper)
            val myRunnable = Runnable { onPlayerStateChanged!!.accept(playerState, currentPlaying) }
            mainHandler.post(myRunnable)
        }
        if (playerState === PlayerState.STARTED && positionCache == null) {
            positionCache = PositionCache()
            val thread = Thread(positionCache)
            thread.start()
        } else if (playerState !== PlayerState.STARTED && positionCache != null) {
            positionCache!!.stop()
            positionCache = null
        }
    }

    /*
    * Set the current playing file. It's called with null to reset the player.
    */
    @Synchronized
    fun setCurrentPlaying(currentPlaying: DownloadFile?) {
        Timber.v("setCurrentPlaying %s", currentPlaying)
        this.currentPlaying = currentPlaying
        updateRemoteControl()

        if (onCurrentPlayingChanged != null) {
            val mainHandler = Handler(context.mainLooper)
            val myRunnable = Runnable { onCurrentPlayingChanged!!.accept(currentPlaying) }
            mainHandler.post(myRunnable)
        }
    }

    /*
    * Set the next playing file. nextToPlay cannot be null
    */
    @Synchronized
    fun setNextPlaying(nextToPlay: DownloadFile) {
        nextPlaying = nextToPlay
        nextPlayingTask = CheckCompletionTask(nextPlaying)
        nextPlayingTask?.start()
    }

    /*
    * Clear the next playing file. setIdle controls whether the playerState is affected as well
    */
    @Synchronized
    fun clearNextPlaying(setIdle: Boolean) {
        nextSetup = false
        nextPlaying = null
        if (nextPlayingTask != null) {
            nextPlayingTask!!.cancel()
            nextPlayingTask = null
        }

        if (setIdle) {
            setNextPlayerState(PlayerState.IDLE)
        }
    }

    @Synchronized
    fun setNextPlayerState(playerState: PlayerState) {
        Timber.i("Next: %s -> %s (%s)", nextPlayerState.name, playerState.name, nextPlaying)
        nextPlayerState = playerState
    }

    /*
    * Public method to play a given file.
    * Optionally specify a position to start at.
    */
    @Synchronized
    @JvmOverloads
    fun play(fileToPlay: DownloadFile?, position: Int = 0, autoStart: Boolean = true) {
        if (nextPlayingTask != null) {
            nextPlayingTask!!.cancel()
            nextPlayingTask = null
        }
        setCurrentPlaying(fileToPlay)

        if (fileToPlay != null) {
            bufferAndPlay(fileToPlay, position, autoStart)
        }
    }


    @Synchronized
    fun playNext() {
        if (nextMediaPlayer == null || currentPlaying == null) return

        val oldPlayer = mediaPlayer
        mediaPlayer = nextMediaPlayer!!

        // FIXME: Why is this being done?
        nextMediaPlayer = oldPlayer

        setCurrentPlaying(nextPlaying)
        setPlayerState(PlayerState.STARTED)

        // FIXME: Why is currentPlaying passed here and not nextPlaying?!
        attachHandlersToPlayer(mediaPlayer, currentPlaying!!, false)

        postRunnable(onNextSongRequested)

        // Proxy should not be being used here since the next player was already setup to play
        proxy?.stop()
        proxy = null
    }



    @Synchronized
    fun pause() {
        try {
            mediaPlayer.pause()
        } catch (x: Exception) {
            handleError(x)
        }
    }

    @Synchronized
    fun start() {
        try {
            mediaPlayer.start()
        } catch (x: Exception) {
            handleError(x)
        }
    }


    /*
     * The remote control API is deprecated in API 21
     */
    private fun updateRemoteControl() {
        if (!Util.isLockScreenEnabled(context)) {
            clearRemoteControl()
            return
        }

        if (remoteControlClient == null) {
            remoteControlClient = createRemoteControlClient()
        } else {
            // FIXME: This looks like a hack. Why is it needed?
            audioManager.unregisterRemoteControlClient(remoteControlClient)
            audioManager.registerRemoteControlClient(remoteControlClient)
        }

        Timber.i("In updateRemoteControl, playerState: %s [%d]", playerState, playerPosition)

        if (playerState === PlayerState.STARTED) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING)
            } else {
                remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING, playerPosition.toLong(), 1.0f)
            }
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED)
            } else {
                remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED, playerPosition.toLong(), 1.0f)
            }
        }

        if (currentPlaying != null) {
            val currentSong = currentPlaying!!.song
            val lockScreenBitmap = FileUtil.getAlbumArtBitmap(context, currentSong, Util.getMinDisplayMetric(context), true)
            val artist = currentSong.artist
            val album = currentSong.album
            val title = currentSong.title
            val currentSongDuration = currentSong.duration
            var duration = 0L
            if (currentSongDuration != null) duration = (currentSongDuration * 1000).toLong()
            remoteControlClient!!.editMetadata(true)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artist)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
                    .putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, lockScreenBitmap)
                    .apply()
        }
    }

    fun clearRemoteControl() {
        if (remoteControlClient != null) {
            remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED)
            audioManager.unregisterRemoteControlClient(remoteControlClient)
            remoteControlClient = null
        }
    }

    private fun setUpRemoteControlClient() {
        if (!Util.isLockScreenEnabled(context)) return

        if (remoteControlClient == null) {
            remoteControlClient = createRemoteControlClient()
        }
    }

    private fun createRemoteControlClient(): RemoteControlClient {
        val componentName = ComponentName(context.packageName, MediaButtonIntentReceiver::class.java.name)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = componentName

        val broadcast = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val remoteControlClient = RemoteControlClient(broadcast)
        audioManager.registerRemoteControlClient(remoteControlClient)

        // Flags for the media transport control that this client supports.
        var flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS or
                RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY or
                RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or
                RemoteControlClient.FLAG_KEY_MEDIA_STOP

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            flags = flags or RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE
            remoteControlClient.setOnGetPlaybackPositionListener { mediaPlayer.currentPosition.toLong() }
            remoteControlClient.setPlaybackPositionUpdateListener { newPositionMs -> seekTo(newPositionMs.toInt()) }
        }

        remoteControlClient.setTransportControlFlags(flags)

        return remoteControlClient
    }

    @Synchronized
    fun seekTo(position: Int) {
        try {
            mediaPlayer.seekTo(position)
            cachedPosition = position
            updateRemoteControl()
        } catch (x: Exception) {
            handleError(x)
        }
    }

    @get:Synchronized
    val playerPosition: Int
        get() = try {
            if (playerState === PlayerState.IDLE || playerState === PlayerState.DOWNLOADING || playerState === PlayerState.PREPARING) {
                0
            } else cachedPosition
        } catch (x: Exception) {
            handleError(x)
            0
        }

    @get:Synchronized
    val playerDuration: Int
        get() {
            if (currentPlaying != null) {
                val duration = currentPlaying!!.song.duration
                if (duration != null) {
                    return duration * 1000
                }
            }
            if (playerState !== PlayerState.IDLE && playerState !== PlayerState.DOWNLOADING && playerState !== PlayerState.PREPARING) {
                try {
                    return mediaPlayer.duration
                } catch (x: Exception) {
                    handleError(x)
                }
            }
            return 0
        }

    fun setVolume(volume: Float) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume, volume)
        }
    }

    @Synchronized
    private fun bufferAndPlay(fileToPlay: DownloadFile, position: Int, autoStart: Boolean) {
        if (playerState !== PlayerState.PREPARED) {
            reset()
            bufferTask = BufferTask(fileToPlay, position)
            bufferTask!!.start()
        } else {
            doPlay(fileToPlay, position, autoStart)
        }
    }

    @Synchronized
    private fun doPlay(downloadFile: DownloadFile, position: Int, start: Boolean) {
        try {
            downloadFile.setPlaying(false)

            val file = if (downloadFile.isCompleteFileAvailable) downloadFile.completeFile else downloadFile.partialFile
            val partial = file == downloadFile.partialFile

            downloadFile.updateModificationDate()
            mediaPlayer.setOnCompletionListener(null)
            secondaryProgress = -1 // Ensure seeking in non StreamProxy playback works
            mediaPlayer.reset()
            setPlayerState(PlayerState.IDLE)
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

            var dataSource = file.path
            if (partial) {
                if (proxy == null) {
                    proxy = StreamProxy(object : Supplier<DownloadFile?>() {
                        override fun get(): DownloadFile {
                            return currentPlaying!!
                        }
                    })
                    proxy!!.start()
                }
                dataSource = String.format(Locale.getDefault(), "http://127.0.0.1:%d/%s",
                        proxy!!.port, URLEncoder.encode(dataSource, Constants.UTF_8))
                Timber.i("Data Source: %s", dataSource)
            } else if (proxy != null) {
                proxy?.stop()
                proxy = null
            }

            Timber.i("Preparing media player")

            mediaPlayer.setDataSource(dataSource)
            setPlayerState(PlayerState.PREPARING)

            mediaPlayer.setOnBufferingUpdateListener { mp, percent ->
                val progressBar = PlayerFragment.getProgressBar()
                val song = downloadFile.song
                if (percent == 100) {
                    if (progressBar != null) {
                        progressBar.secondaryProgress = 100 * progressBar.max
                    }
                    mp.setOnBufferingUpdateListener(null)
                } else if (progressBar != null && song.transcodedContentType == null && Util.getMaxBitRate(context) == 0) {
                    secondaryProgress = (percent.toDouble() / 100.toDouble() * progressBar.max).toInt()
                    progressBar.secondaryProgress = secondaryProgress
                }
            }

            mediaPlayer.setOnPreparedListener {
                Timber.i("Media player prepared")
                setPlayerState(PlayerState.PREPARED)
                val progressBar = PlayerFragment.getProgressBar()
                if (progressBar != null && downloadFile.isWorkDone) {
                    // Populate seek bar secondary progress if we have a complete file for consistency
                    PlayerFragment.getProgressBar().secondaryProgress = 100 * progressBar.max
                }
                synchronized(this@LocalMediaPlayer) {
                    if (position != 0) {
                        Timber.i("Restarting player from position %d", position)
                        seekTo(position)
                    }
                    cachedPosition = position
                    if (start) {
                        mediaPlayer.start()
                        setPlayerState(PlayerState.STARTED)
                    } else {
                        setPlayerState(PlayerState.PAUSED)
                    }
                }

                postRunnable(onPrepared)

            }
            attachHandlersToPlayer(mediaPlayer, downloadFile, partial)
            mediaPlayer.prepareAsync()
        } catch (x: Exception) {
            handleError(x)
        }
    }

    @Synchronized
    private fun setupNext(downloadFile: DownloadFile) {
        try {
            val file = if (downloadFile.isCompleteFileAvailable) downloadFile.completeFile else downloadFile.partialFile
            if (nextMediaPlayer != null) {
                nextMediaPlayer!!.setOnCompletionListener(null)
                nextMediaPlayer!!.release()
                nextMediaPlayer = null
            }
            nextMediaPlayer = MediaPlayer()
            nextMediaPlayer!!.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            try {
                nextMediaPlayer!!.audioSessionId = mediaPlayer.audioSessionId
            } catch (e: Throwable) {
                nextMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
            nextMediaPlayer!!.setDataSource(file.path)
            setNextPlayerState(PlayerState.PREPARING)
            nextMediaPlayer!!.setOnPreparedListener {
                try {
                    setNextPlayerState(PlayerState.PREPARED)
                    if (Util.getGaplessPlaybackPreference(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED)) {
                        mediaPlayer.setNextMediaPlayer(nextMediaPlayer)
                        nextSetup = true
                    }
                } catch (x: Exception) {
                    handleErrorNext(x)
                }
            }
            nextMediaPlayer!!.setOnErrorListener { mediaPlayer, what, extra ->
                Timber.w("Error on playing next (%d, %d): %s", what, extra, downloadFile)
                true
            }
            nextMediaPlayer!!.prepareAsync()
        } catch (x: Exception) {
            handleErrorNext(x)
        }
    }

    private fun attachHandlersToPlayer(mediaPlayer: MediaPlayer, downloadFile: DownloadFile, isPartial: Boolean) {
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Timber.w("Error on playing file (%d, %d): %s", what, extra, downloadFile)
            val pos = cachedPosition
            reset()
            downloadFile.setPlaying(false)
            doPlay(downloadFile, pos, true)
            downloadFile.setPlaying(true)
            true
        }

        val duration = if (downloadFile.song.duration == null) 0 else downloadFile.song.duration!! * 1000

        mediaPlayer.setOnCompletionListener(object : OnCompletionListener {
            override fun onCompletion(mediaPlayer: MediaPlayer) {
                // Acquire a temporary wakelock, since when we return from
                // this callback the MediaPlayer will release its wakelock
                // and allow the device to go to sleep.
                wakeLock.acquire(60000)
                val pos = cachedPosition
                Timber.i("Ending position %d of %d", pos, duration)
                if (!isPartial || downloadFile.isWorkDone && abs(duration - pos) < 1000) {
                    setPlayerState(PlayerState.COMPLETED)
                    if (Util.getGaplessPlaybackPreference(context) && nextPlaying != null && nextPlayerState === PlayerState.PREPARED) {
                        if (nextSetup) {
                            nextSetup = false
                        }
                        playNext()
                    } else {
                        if (onSongCompleted != null) {
                            val mainHandler = Handler(context.mainLooper)
                            val myRunnable = Runnable { onSongCompleted!!.accept(currentPlaying) }
                            mainHandler.post(myRunnable)
                        }
                    }
                    return
                }
                synchronized(this) {
                    if (downloadFile.isWorkDone) {
                        // Complete was called early even though file is fully buffered
                        Timber.i("Requesting restart from %d of %d", pos, duration)
                        reset()
                        downloadFile.setPlaying(false)
                        doPlay(downloadFile, pos, true)
                        downloadFile.setPlaying(true)
                    } else {
                        Timber.i("Requesting restart from %d of %d", pos, duration)
                        reset()
                        bufferTask = BufferTask(downloadFile, pos)
                        bufferTask!!.start()
                    }
                }
            }
        })
    }

    @Synchronized
    fun reset() {
        if (bufferTask != null) {
            bufferTask!!.cancel()
        }
        try {
            setPlayerState(PlayerState.IDLE)
            mediaPlayer.setOnErrorListener(null)
            mediaPlayer.setOnCompletionListener(null)
            mediaPlayer.reset()
        } catch (x: Exception) {
            handleError(x)
        }
    }

    private inner class BufferTask(private val downloadFile: DownloadFile?, private val position: Int) : CancellableTask() {
        private val expectedFileSize: Long
        private val partialFile: File
        override fun execute() {
            setPlayerState(PlayerState.DOWNLOADING)
            while (!bufferComplete() && !isOffline(context)) {
                Util.sleepQuietly(1000L)
                if (isCancelled) {
                    return
                }
            }
            if (downloadFile != null) {
                doPlay(downloadFile, position, true)
            }
        }

        private fun bufferComplete(): Boolean {
            val completeFileAvailable = downloadFile!!.isWorkDone
            val size = partialFile.length()
            Timber.i("Buffering %s (%d/%d, %s)", partialFile, size, expectedFileSize, completeFileAvailable)
            return completeFileAvailable || size >= expectedFileSize
        }

        override fun toString(): String {
            return String.format("BufferTask (%s)", downloadFile)
        }

        init {
            partialFile = downloadFile!!.partialFile
            var bufferLength = Util.getBufferLength(context).toLong()
            if (bufferLength == 0L) {
                // Set to seconds in a day, basically infinity
                bufferLength = 86400L
            }

            // Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
            val bitRate = downloadFile.bitRate
            val byteCount = max(100000, bitRate * 1024L / 8L * bufferLength)

            // Find out how large the file should grow before resuming playback.
            Timber.i("Buffering from position %d and bitrate %d", position, bitRate)
            expectedFileSize = position * bitRate / 8 + byteCount
        }
    }

    private inner class CheckCompletionTask(downloadFile: DownloadFile?) : CancellableTask() {
        private val downloadFile: DownloadFile?
        private val partialFile: File?
        override fun execute() {
            Thread.currentThread().name = "CheckCompletionTask"
            if (downloadFile == null) {
                return
            }

            // Do an initial sleep so this prepare can't compete with main prepare
            Util.sleepQuietly(5000L)
            while (!bufferComplete()) {
                Util.sleepQuietly(5000L)
                if (isCancelled) {
                    return
                }
            }

            // Start the setup of the next media player
            mediaPlayerHandler!!.post { setupNext(downloadFile) }
        }

        private fun bufferComplete(): Boolean {
            val completeFileAvailable = downloadFile!!.isWorkDone
            Timber.i("Buffering next %s (%d)", partialFile, partialFile!!.length())
            return completeFileAvailable && (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED)
        }

        override fun toString(): String {
            return String.format("CheckCompletionTask (%s)", downloadFile)
        }

        init {
            setNextPlayerState(PlayerState.IDLE)
            this.downloadFile = downloadFile
            partialFile = downloadFile?.partialFile
        }
    }

    private inner class PositionCache : Runnable {
        var isRunning = true
        fun stop() {
            isRunning = false
        }

        override fun run() {
            Thread.currentThread().name = "PositionCache"

            // Stop checking position before the song reaches completion
            while (isRunning) {
                try {
                    if (mediaPlayer != null && playerState === PlayerState.STARTED) {
                        cachedPosition = mediaPlayer.currentPosition
                    }
                    Util.sleepQuietly(50L)
                } catch (e: Exception) {
                    Timber.w(e, "Crashed getting current position")
                    isRunning = false
                    positionCache = null
                }
            }
        }
    }

    private fun handleError(x: Exception) {
        Timber.w(x, "Media player error")
        try {
            mediaPlayer.reset()
        } catch (ex: Exception) {
            Timber.w(ex, "Exception encountered when resetting media player")
        }
    }

    private fun handleErrorNext(x: Exception) {
        Timber.w(x, "Next Media player error")
        nextMediaPlayer!!.reset()
    }

    private fun postRunnable(runnable: Runnable?) {
        if (runnable != null) {
            val mainHandler = Handler(context.mainLooper)
            val myRunnable = Runnable { runnable.run() }
            mainHandler.post(myRunnable)
        }
    }

}