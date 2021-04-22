/*
 * LocalMediaPlayer.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import android.os.PowerManager.WakeLock
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.audiofx.VisualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.fragment.PlayerFragment
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.StreamProxy
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Represents a Media Player which uses the mobile's resources for playback
 */
class LocalMediaPlayer(
    private val audioFocusHandler: AudioFocusHandler,
    private val context: Context
) {

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
    private var bufferTask: CancellableTask? = null
    private var positionCache: PositionCache? = null
    private var secondaryProgress = -1
    private val pm = context.getSystemService(POWER_SERVICE) as PowerManager
    private val wakeLock: WakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, this.javaClass.name)

    fun init() {
        Thread {
            Thread.currentThread().name = "MediaPlayerThread"
            Looper.prepare()
            mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setOnErrorListener { _, what, more ->
                handleError(
                    Exception(
                        String.format(
                            Locale.getDefault(),
                            "MediaPlayer error: %d (%d)", what, more
                        )
                    )
                )
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
        Timber.i("LocalMediaPlayer created")
    }

    fun release() {
        reset()
        try {
            val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.audioSessionId)
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(i)
            EqualizerController.release()
            VisualizerController.release()
            mediaPlayer.release()

            mediaPlayer = MediaPlayer()

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

        if (onPlayerStateChanged != null) {
            val mainHandler = Handler(context.mainLooper)
            val myRunnable = Runnable {
                onPlayerStateChanged!!.accept(playerState, currentPlaying)
            }
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
        if (nextMediaPlayer == null || nextPlaying == null) return

        mediaPlayer = nextMediaPlayer!!

        setCurrentPlaying(nextPlaying)
        setPlayerState(PlayerState.STARTED)

        attachHandlersToPlayer(mediaPlayer, nextPlaying!!, false)

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

    @Synchronized
    fun seekTo(position: Int) {
        try {
            mediaPlayer.seekTo(position)
            cachedPosition = position
        } catch (x: Exception) {
            handleError(x)
        }
    }

    @get:Synchronized
    val playerPosition: Int
        get() = try {
            when (playerState) {
                PlayerState.IDLE -> 0
                PlayerState.DOWNLOADING -> 0
                PlayerState.PREPARING -> 0
                else -> cachedPosition
            }
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
            if (playerState !== PlayerState.IDLE &&
                playerState !== PlayerState.DOWNLOADING &&
                playerState !== PlayerState.PREPARING
            ) {
                try {
                    return mediaPlayer.duration
                } catch (x: Exception) {
                    handleError(x)
                }
            }
            return 0
        }

    fun setVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    @Synchronized
    private fun bufferAndPlay(fileToPlay: DownloadFile, position: Int, autoStart: Boolean) {
        if (playerState !== PlayerState.PREPARED) {
            reset()
            bufferTask = BufferTask(fileToPlay, position, autoStart)
            bufferTask!!.start()
        } else {
            doPlay(fileToPlay, position, autoStart)
        }
    }

    @Synchronized
    private fun doPlay(downloadFile: DownloadFile, position: Int, start: Boolean) {

        // In many cases we will be resetting the mediaPlayer a second time here.
        // figure out if we can remove this call...
        resetMediaPlayer()

        try {
            downloadFile.setPlaying(false)

            val file = downloadFile.completeOrPartialFile
            val partial = !downloadFile.isCompleteFileAvailable

            downloadFile.updateModificationDate()
            mediaPlayer.setOnCompletionListener(null)
            secondaryProgress = -1 // Ensure seeking in non StreamProxy playback works

            setPlayerState(PlayerState.IDLE)
            setAudioAttributes(mediaPlayer)

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
                dataSource = String.format(
                    Locale.getDefault(), "http://127.0.0.1:%d/%s",
                    proxy!!.port, URLEncoder.encode(dataSource, Constants.UTF_8)
                )
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
                    mp.setOnBufferingUpdateListener(null)
                }

                secondaryProgress = (percent.toDouble() / 100.toDouble() * progressBar.max).toInt()

                if (song.transcodedContentType == null && Util.getMaxBitRate(context) == 0) {
                    progressBar?.secondaryProgress = secondaryProgress
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

    private fun setAudioAttributes(player: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(AudioFocusHandler.getAudioAttributes())
        } else {
            @Suppress("DEPRECATION")
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }

    @Synchronized
    private fun setupNext(downloadFile: DownloadFile) {
        try {
            val file = downloadFile.completeOrPartialFile

            // Release the media player if it is not our active player
            if (nextMediaPlayer != null && nextMediaPlayer != mediaPlayer) {
                nextMediaPlayer!!.setOnCompletionListener(null)
                nextMediaPlayer!!.release()
                nextMediaPlayer = null
            }
            nextMediaPlayer = MediaPlayer()
            nextMediaPlayer!!.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)

            setAudioAttributes(nextMediaPlayer!!)


            // This has nothing to do with the MediaSession, it is used to associate
            // the equalizer or visualizer with the player
            try {
                nextMediaPlayer!!.audioSessionId = mediaPlayer.audioSessionId
            } catch (e: Throwable) {
            }

            nextMediaPlayer!!.setDataSource(file.path)
            setNextPlayerState(PlayerState.PREPARING)
            nextMediaPlayer!!.setOnPreparedListener {
                try {
                    setNextPlayerState(PlayerState.PREPARED)
                    if (Util.getGaplessPlaybackPreference(context) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                        (
                            playerState === PlayerState.STARTED ||
                                playerState === PlayerState.PAUSED
                            )
                    ) {
                        mediaPlayer.setNextMediaPlayer(nextMediaPlayer)
                        nextSetup = true
                    }
                } catch (x: Exception) {
                    handleErrorNext(x)
                }
            }
            nextMediaPlayer!!.setOnErrorListener { _, what, extra ->
                Timber.w("Error on playing next (%d, %d): %s", what, extra, downloadFile)
                true
            }
            nextMediaPlayer!!.prepareAsync()
        } catch (x: Exception) {
            handleErrorNext(x)
        }
    }

    private fun attachHandlersToPlayer(
        mediaPlayer: MediaPlayer,
        downloadFile: DownloadFile,
        isPartial: Boolean
    ) {
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Timber.w("Error on playing file (%d, %d): %s", what, extra, downloadFile)
            val pos = cachedPosition
            reset()
            downloadFile.setPlaying(false)
            doPlay(downloadFile, pos, true)
            downloadFile.setPlaying(true)
            true
        }

        var duration = 0
        if (downloadFile.song.duration != null) {
            duration = downloadFile.song.duration!! * 1000
        }

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
                    if (Util.getGaplessPlaybackPreference(context) &&
                        nextPlaying != null &&
                        nextPlayerState === PlayerState.PREPARED
                    ) {
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

        resetMediaPlayer()

        try {
            setPlayerState(PlayerState.IDLE)
            mediaPlayer.setOnErrorListener(null)
            mediaPlayer.setOnCompletionListener(null)
        } catch (x: Exception) {
            handleError(x)
        }
    }

    @Synchronized
    fun resetMediaPlayer() {
        try {
            mediaPlayer.reset()
        } catch (x: Exception) {
            Timber.w(x, "MediaPlayer was released but LocalMediaPlayer was not destroyed")

            // Recreate MediaPlayer
            mediaPlayer = MediaPlayer()
        }
    }

    private inner class BufferTask(
        private val downloadFile: DownloadFile,
        private val position: Int,
        private val autoStart: Boolean = true
    ) : CancellableTask() {
        private val expectedFileSize: Long
        private val partialFile: File = downloadFile.partialFile

        override fun execute() {
            setPlayerState(PlayerState.DOWNLOADING)
            while (!bufferComplete() && !isOffline(context)) {
                Util.sleepQuietly(1000L)
                if (isCancelled) {
                    return
                }
            }

            doPlay(downloadFile, position, autoStart)
        }

        private fun bufferComplete(): Boolean {
            val completeFileAvailable = downloadFile.isWorkDone
            val size = partialFile.length()

            Timber.i(
                "Buffering %s (%d/%d, %s)",
                partialFile, size, expectedFileSize, completeFileAvailable
            )

            return completeFileAvailable || size >= expectedFileSize
        }

        override fun toString(): String {
            return String.format("BufferTask (%s)", downloadFile)
        }

        init {
            var bufferLength = Util.getBufferLength(context).toLong()
            if (bufferLength == 0L) {
                // Set to seconds in a day, basically infinity
                bufferLength = 86400L
            }

            // Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
            val bitRate = downloadFile.getBitRate()
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
            val state = (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED)

            Timber.i("Buffering next %s (%d)", partialFile, partialFile!!.length())

            return completeFileAvailable && state
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
                    if (playerState === PlayerState.STARTED) {
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
