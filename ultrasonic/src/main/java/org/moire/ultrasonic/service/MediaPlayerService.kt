package org.moire.ultrasonic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.RepeatMode
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4
import org.moire.ultrasonic.service.MediaPlayerService
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.NowPlayingEventDistributor
import org.moire.ultrasonic.util.ShufflePlayBuffer
import org.moire.ultrasonic.util.SimpleServiceBinder
import org.moire.ultrasonic.util.Util
import timber.log.Timber
import java.util.ArrayList

/**
 * Android Foreground Service for playing music
 * while the rest of the Ultrasonic App is in the background.
 */
class MediaPlayerService : Service() {
    private val binder: IBinder = SimpleServiceBinder(this)
    private val scrobbler = Scrobbler()
    var jukeboxMediaPlayer = inject(JukeboxMediaPlayer::class.java)
    private val downloadQueueSerializerLazy = inject(DownloadQueueSerializer::class.java)
    private val shufflePlayBufferLazy = inject(ShufflePlayBuffer::class.java)
    private val downloaderLazy = inject(Downloader::class.java)
    private val localMediaPlayerLazy = inject(LocalMediaPlayer::class.java)
    private val nowPlayingEventDistributor = inject(NowPlayingEventDistributor::class.java)
    private val mediaPlayerLifecycleSupport = inject(MediaPlayerLifecycleSupport::class.java)
    private var localMediaPlayer: LocalMediaPlayer? = null
    private var downloader: Downloader? = null
    private var shufflePlayBuffer: ShufflePlayBuffer? = null
    private var downloadQueueSerializer: DownloadQueueSerializer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionToken: MediaSessionCompat.Token? = null
    private var isInForeground = false
    private var notificationBuilder: NotificationCompat.Builder? = null
    val repeatMode: RepeatMode
        get() = Util.getRepeatMode(this)

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        downloader = downloaderLazy.value
        localMediaPlayer = localMediaPlayerLazy.value
        shufflePlayBuffer = shufflePlayBufferLazy.value
        downloadQueueSerializer = downloadQueueSerializerLazy.value
        initMediaSessions()
        downloader!!.onCreate()
        shufflePlayBuffer!!.onCreate()
        localMediaPlayer!!.init()
        setupOnCurrentPlayingChangedHandler()
        setupOnPlayerStateChangedHandler()
        setupOnSongCompletedHandler()
        localMediaPlayer!!.onPrepared = {
            downloadQueueSerializer!!.serializeDownloadQueue(
                    downloader!!.downloadList,
                    downloader!!.currentPlayingIndex,
                    playerPosition
            )
            null
        }
        localMediaPlayer!!.onNextSongRequested = Runnable { setNextPlaying() }

        // Create Notification Channel
        createNotificationChannel()

        // Update notification early. It is better to show an empty one temporarily than waiting too long and letting Android kill the app
        updateNotification(PlayerState.IDLE, null)
        instance = this
        Timber.i("MediaPlayerService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            localMediaPlayer!!.release()
            downloader!!.stop()
            shufflePlayBuffer!!.onDestroy()
            mediaSession!!.release()
        } catch (ignored: Throwable) {
        }
        Timber.i("MediaPlayerService stopped")
    }

    private fun stopIfIdle() {
        synchronized(instanceLock) {
            // currentPlaying could be changed from another thread in the meantime, so check again before stopping for good
            if (localMediaPlayer!!.currentPlaying == null || localMediaPlayer!!.playerState === PlayerState.STOPPED) stopSelf()
        }
    }

    @Synchronized
    fun seekTo(position: Int) {
        if (jukeboxMediaPlayer.value.isEnabled) {
            jukeboxMediaPlayer.value.skip(downloader!!.currentPlayingIndex, position / 1000)
        } else {
            localMediaPlayer!!.seekTo(position)
        }
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            if (localMediaPlayer!!.playerState === PlayerState.IDLE || localMediaPlayer!!.playerState === PlayerState.DOWNLOADING || localMediaPlayer!!.playerState === PlayerState.PREPARING) {
                return 0
            }
            return if (jukeboxMediaPlayer.value.isEnabled) jukeboxMediaPlayer.value.positionSeconds * 1000 else localMediaPlayer!!.playerPosition
        }

    @get:Synchronized
    val playerDuration: Int
        get() = localMediaPlayer!!.playerDuration

    @Synchronized
    fun setCurrentPlaying(currentPlayingIndex: Int) {
        try {
            localMediaPlayer!!.setCurrentPlaying(downloader!!.downloadList[currentPlayingIndex])
        } catch (x: IndexOutOfBoundsException) {
            // Ignored
        }
    }

    fun setupOnCurrentPlayingChangedHandler() {
        localMediaPlayer!!.onCurrentPlayingChanged = { currentPlaying: DownloadFile? ->
            if (currentPlaying != null) {
                Util.broadcastNewTrackInfo(this@MediaPlayerService, currentPlaying.song)
                Util.broadcastA2dpMetaDataChange(this@MediaPlayerService, playerPosition, currentPlaying,
                        downloader!!.downloads.size, downloader!!.currentPlayingIndex + 1)
            } else {
                Util.broadcastNewTrackInfo(this@MediaPlayerService, null)
                Util.broadcastA2dpMetaDataChange(this@MediaPlayerService, playerPosition, null,
                        downloader!!.downloads.size, downloader!!.currentPlayingIndex + 1)
            }

            // Update widget
            val playerState = localMediaPlayer!!.playerState
            val song = currentPlaying?.song
            UpdateWidget(playerState, song)
            if (currentPlaying != null) {
                updateNotification(localMediaPlayer!!.playerState, currentPlaying)
                nowPlayingEventDistributor.value.raiseShowNowPlayingEvent()
            } else {
                nowPlayingEventDistributor.value.raiseHideNowPlayingEvent()
                stopForeground(true)
                isInForeground = false
                stopIfIdle()
            }
            null
        }
    }

    @Synchronized
    fun setNextPlaying() {
        val gaplessPlayback = Util.getGaplessPlaybackPreference(this)
        if (!gaplessPlayback) {
            localMediaPlayer!!.clearNextPlaying(true)
            return
        }
        var index = downloader!!.currentPlayingIndex
        if (index != -1) {
            when (repeatMode) {
                RepeatMode.OFF -> index += 1
                RepeatMode.ALL -> index = (index + 1) % downloader!!.downloadList.size
                RepeatMode.SINGLE -> {
                }
                else -> {
                }
            }
        }
        localMediaPlayer!!.clearNextPlaying(false)
        if (index < downloader!!.downloadList.size && index != -1) {
            localMediaPlayer!!.setNextPlaying(downloader!!.downloadList[index])
        } else {
            localMediaPlayer!!.clearNextPlaying(true)
        }
    }

    @Synchronized
    fun togglePlayPause() {
        if (localMediaPlayer!!.playerState === PlayerState.PAUSED || localMediaPlayer!!.playerState === PlayerState.COMPLETED || localMediaPlayer!!.playerState === PlayerState.STOPPED) {
            start()
        } else if (localMediaPlayer!!.playerState === PlayerState.IDLE) {
            play()
        } else if (localMediaPlayer!!.playerState === PlayerState.STARTED) {
            pause()
        }
    }

    @Synchronized
    fun resumeOrPlay() {
        if (localMediaPlayer!!.playerState === PlayerState.PAUSED || localMediaPlayer!!.playerState === PlayerState.COMPLETED || localMediaPlayer!!.playerState === PlayerState.STOPPED) {
            start()
        } else if (localMediaPlayer!!.playerState === PlayerState.IDLE) {
            play()
        }
    }

    /**
     * Plays either the current song (resume) or the first/next one in queue.
     */
    @Synchronized
    fun play() {
        val current = downloader!!.currentPlayingIndex
        if (current == -1) {
            play(0)
        } else {
            play(current)
        }
    }

    @Synchronized
    fun play(index: Int) {
        play(index, true)
    }

    @Synchronized
    fun play(index: Int, start: Boolean) {
        Timber.v("play requested for %d", index)
        if (index < 0 || index >= downloader!!.downloadList.size) {
            resetPlayback()
        } else {
            setCurrentPlaying(index)
            if (start) {
                if (jukeboxMediaPlayer.value.isEnabled) {
                    jukeboxMediaPlayer.value.skip(index, 0)
                    localMediaPlayer!!.setPlayerState(PlayerState.STARTED)
                } else {
                    localMediaPlayer!!.play(downloader!!.downloadList[index])
                }
            }
            downloader!!.checkDownloads()
            setNextPlaying()
        }
    }

    @Synchronized
    private fun resetPlayback() {
        localMediaPlayer!!.reset()
        localMediaPlayer!!.setCurrentPlaying(null)
        downloadQueueSerializer!!.serializeDownloadQueue(downloader!!.downloadList,
                downloader!!.currentPlayingIndex, playerPosition)
    }

    @Synchronized
    fun pause() {
        if (localMediaPlayer!!.playerState === PlayerState.STARTED) {
            if (jukeboxMediaPlayer.value.isEnabled) {
                jukeboxMediaPlayer.value.stop()
            } else {
                localMediaPlayer!!.pause()
            }
            localMediaPlayer!!.setPlayerState(PlayerState.PAUSED)
        }
    }

    @Synchronized
    fun stop() {
        if (localMediaPlayer!!.playerState === PlayerState.STARTED) {
            if (jukeboxMediaPlayer.value.isEnabled) {
                jukeboxMediaPlayer.value.stop()
            } else {
                localMediaPlayer!!.pause()
            }
        }
        localMediaPlayer!!.setPlayerState(PlayerState.STOPPED)
    }

    @Synchronized
    fun start() {
        if (jukeboxMediaPlayer.value.isEnabled) {
            jukeboxMediaPlayer.value.start()
        } else {
            localMediaPlayer!!.start()
        }
        localMediaPlayer!!.setPlayerState(PlayerState.STARTED)
    }

    private fun UpdateWidget(playerState: PlayerState, song: MusicDirectory.Entry?) {
        UltrasonicAppWidgetProvider4X1.getInstance().notifyChange(this@MediaPlayerService, song, playerState === PlayerState.STARTED, false)
        UltrasonicAppWidgetProvider4X2.getInstance().notifyChange(this@MediaPlayerService, song, playerState === PlayerState.STARTED, true)
        UltrasonicAppWidgetProvider4X3.getInstance().notifyChange(this@MediaPlayerService, song, playerState === PlayerState.STARTED, false)
        UltrasonicAppWidgetProvider4X4.getInstance().notifyChange(this@MediaPlayerService, song, playerState === PlayerState.STARTED, false)
    }

    fun setupOnPlayerStateChangedHandler() {
        localMediaPlayer!!.onPlayerStateChanged = { playerState: PlayerState, currentPlaying: DownloadFile? ->
            // Notify MediaSession
            updateMediaSession(currentPlaying, playerState)
            if (playerState === PlayerState.PAUSED) {
                downloadQueueSerializer!!.serializeDownloadQueue(downloader!!.downloadList, downloader!!.currentPlayingIndex, playerPosition)
            }
            val showWhenPaused = playerState !== PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(this@MediaPlayerService)
            val show = playerState === PlayerState.STARTED || showWhenPaused
            val song = currentPlaying?.song
            Util.broadcastPlaybackStatusChange(this@MediaPlayerService, playerState)
            Util.broadcastA2dpPlayStatusChange(this@MediaPlayerService, playerState, song,
                    downloader!!.downloadList.size + downloader!!.backgroundDownloadList.size,
                    downloader!!.downloadList.indexOf(currentPlaying) + 1, playerPosition)

            // Update widget
            UpdateWidget(playerState, song)
            if (show) {
                // Only update notification if player state is one that will change the icon
                if (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED) {
                    updateNotification(playerState, currentPlaying)
                    nowPlayingEventDistributor.value.raiseShowNowPlayingEvent()
                }
            } else {
                nowPlayingEventDistributor.value.raiseHideNowPlayingEvent()
                stopForeground(true)
                isInForeground = false
                stopIfIdle()
            }
            if (playerState === PlayerState.STARTED) {
                scrobbler.scrobble(this@MediaPlayerService, currentPlaying, false)
            } else if (playerState === PlayerState.COMPLETED) {
                scrobbler.scrobble(this@MediaPlayerService, currentPlaying, true)
            }
            null
        }
    }

    private fun setupOnSongCompletedHandler() {
        localMediaPlayer!!.onSongCompleted = { currentPlaying: DownloadFile? ->
            val index = downloader!!.currentPlayingIndex
            if (currentPlaying != null) {
                val song = currentPlaying.song
                if (song.bookmarkPosition > 0 && Util.getShouldClearBookmark(this@MediaPlayerService)) {
                    val musicService = getMusicService(this@MediaPlayerService)
                    try {
                        musicService.deleteBookmark(song.id, this@MediaPlayerService)
                    } catch (ignored: Exception) {
                    }
                }
            }
            if (index != -1) {
                when (repeatMode) {
                    RepeatMode.OFF -> {
                        if (index + 1 < 0 || index + 1 >= downloader!!.downloadList.size) {
                            if (Util.getShouldClearPlaylist(this@MediaPlayerService)) {
                                clear(true)
                                jukeboxMediaPlayer.value.updatePlaylist()
                            }
                            resetPlayback()
                            break
                        }
                        play(index + 1)
                    }
                    RepeatMode.ALL -> play((index + 1) % downloader!!.downloadList.size)
                    RepeatMode.SINGLE -> play(index)
                    else -> {
                    }
                }
            }
            null
        }
    }

    @Synchronized
    fun clear(serialize: Boolean) {
        localMediaPlayer!!.reset()
        downloader!!.clear()
        localMediaPlayer!!.setCurrentPlaying(null)
        setNextPlaying()
        if (serialize) {
            downloadQueueSerializer!!.serializeDownloadQueue(downloader!!.downloadList,
                    downloader!!.currentPlayingIndex, playerPosition)
        }
    }

    private fun updateMediaSession(currentPlaying: DownloadFile?, playerState: PlayerState) {
        // Set Metadata
        val metadata = MediaMetadataCompat.Builder()
        val context = applicationContext
        if (currentPlaying != null) {
            try {
                val song = currentPlaying.song
                val cover = FileUtil.getAlbumArtBitmap(context, song,
                        Util.getMinDisplayMetric(context), true
                )
                metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            } catch (e: Exception) {
                Timber.e(e, "Error setting the metadata")
            }
        }

        // Save the metadata
        mediaSession!!.setMetadata(metadata.build())

        // Create playback State
        val playbackState = PlaybackStateCompat.Builder()
        val state = if (playerState === PlayerState.STARTED) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

        // If we set the playback position correctly, we can get a nice seek bar :)
        playbackState.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        // Set Active state
        mediaSession!!.isActive = playerState === PlayerState.STARTED

        // Save the playback state
        mediaSession!!.setPlaybackState(playbackState.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            channel.lightColor = android.R.color.holo_blue_dark
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(playerState: PlayerState, currentPlaying: DownloadFile?) {
        if (Util.isNotificationEnabled(this)) {
            if (isInForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying))
                } else {
                    val notificationManager = NotificationManagerCompat.from(this)
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying))
                }
                Timber.w("--- Updated notification")
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying))
                isInForeground = true
                Timber.w("--- Created Foreground notification")
            }
        }
    }

    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    private fun buildForegroundNotification(playerState: PlayerState, currentPlaying: DownloadFile?): Notification {
        // Init
        val context = applicationContext
        val song = currentPlaying?.song
        val stopIntent = getPendingIntentForMediaAction(context, KeyEvent.KEYCODE_MEDIA_STOP, 100)

        // We should use a single notification builder, otherwise the notification may not be updated
        if (notificationBuilder == null) {
            notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

            // Set some values that never change
            notificationBuilder!!.setSmallIcon(R.drawable.ic_stat_ultrasonic)
            notificationBuilder!!.setAutoCancel(false)
            notificationBuilder!!.setOngoing(true)
            notificationBuilder!!.setOnlyAlertOnce(true)
            notificationBuilder!!.setWhen(System.currentTimeMillis())
            notificationBuilder!!.setShowWhen(false)
            notificationBuilder!!.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            notificationBuilder!!.priority = NotificationCompat.PRIORITY_LOW

            // Add content intent (when user taps on notification)
            notificationBuilder!!.setContentIntent(pendingIntentForContent)

            // This intent is executed when the user closes the notification
            notificationBuilder!!.setDeleteIntent(stopIntent)
        }

        // Use the Media Style, to enable native Android support for playback notification
        val style = androidx.media.app.NotificationCompat.MediaStyle()
        style.setMediaSession(mediaSessionToken)

        // Clear old actions
        notificationBuilder!!.clearActions()

        // Add actions
        val compactActions = addActions(context, notificationBuilder!!, playerState, song)

        // Configure shortcut actions
        style.setShowActionsInCompactView(*compactActions)
        notificationBuilder!!.setStyle(style)

        // Set song title, artist and cover if possible
        if (song != null) {
            val iconSize = (256 * context.resources.displayMetrics.density).toInt()
            val bitmap = FileUtil.getAlbumArtBitmap(context, song, iconSize, true)
            notificationBuilder!!.setContentTitle(song.title)
            notificationBuilder!!.setContentText(song.artist)
            notificationBuilder!!.setLargeIcon(bitmap)
            notificationBuilder!!.setSubText(song.album)
        }
        return notificationBuilder!!.build()
    }

    private fun addActions(context: Context, notificationBuilder: NotificationCompat.Builder, playerState: PlayerState, song: MusicDirectory.Entry?): IntArray {
        val compactActionList = ArrayList<Int>()
        var numActions = 0 // we start and 0 and then increment by 1 for each call to generateAction


        // Star
        if (song != null) {
            notificationBuilder.addAction(generateStarUnstarAction(context, numActions, song.starred))
        }
        numActions++

        // Next
        notificationBuilder.addAction(generateAction(context, numActions))
        compactActionList.add(numActions)
        numActions++

        // Play/Pause button
        notificationBuilder.addAction(generatePlayPauseAction(context, numActions, playerState))
        compactActionList.add(numActions)
        numActions++

        // Previous
        notificationBuilder.addAction(generateAction(context, numActions))
        compactActionList.add(numActions)
        numActions++

        // Close
        notificationBuilder.addAction(generateAction(context, numActions))
        val actionArray = IntArray(compactActionList.size)
        for (i in actionArray.indices) {
            actionArray[i] = compactActionList[i]
        }
        return actionArray
        //notificationBuilder.setShowActionsInCompactView())
    }

    private fun generateAction(context: Context, requestCode: Int): NotificationCompat.Action? {
        val keycode: Int
        val icon: Int
        val label: String
        when (requestCode) {
            1 -> {
                keycode = KeyEvent.KEYCODE_MEDIA_PREVIOUS
                label = getString(R.string.common_play_previous)
                icon = R.drawable.media_backward_medium_dark
            }
            2 ->                 // Is handled in generatePlayPauseAction()
                return null
            3 -> {
                keycode = KeyEvent.KEYCODE_MEDIA_NEXT
                label = getString(R.string.common_play_next)
                icon = R.drawable.media_forward_medium_dark
            }
            4 -> {
                keycode = KeyEvent.KEYCODE_MEDIA_STOP
                label = getString(R.string.buttons_stop)
                icon = R.drawable.ic_baseline_close_24
            }
            else -> return null
        }
        val pendingIntent = getPendingIntentForMediaAction(context, keycode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun generatePlayPauseAction(context: Context, requestCode: Int, playerState: PlayerState): NotificationCompat.Action {
        val isPlaying = playerState === PlayerState.STARTED
        val pendingIntent = getPendingIntentForMediaAction(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, requestCode)
        val label: String
        val icon: Int
        if (isPlaying) {
            label = getString(R.string.common_pause)
            icon = R.drawable.media_pause_large_dark
        } else {
            label = getString(R.string.common_play)
            icon = R.drawable.media_start_large_dark
        }
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun generateStarUnstarAction(context: Context, requestCode: Int, isStarred: Boolean): NotificationCompat.Action {
        val keyCode: Int
        val label: String
        val icon: Int
        keyCode = KeyEvent.KEYCODE_STAR
        if (isStarred) {
            label = getString(R.string.download_menu_star)
            icon = R.drawable.ic_star_full_dark
        } else {
            label = getString(R.string.download_menu_star)
            icon = R.drawable.ic_star_hollow_dark
        }
        val pendingIntent = getPendingIntentForMediaAction(context, keyCode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private val pendingIntentForContent: PendingIntent
        private get() {
            val notificationIntent = Intent(this, NavigationActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_SHOW_PLAYER, true)
            return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun getPendingIntentForMediaAction(context: Context, keycode: Int, requestCode: Int): PendingIntent {
        val intent = Intent(Constants.CMD_PROCESS_KEYCODE)
        intent.setPackage(context.packageName)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun initMediaSessions() {
        mediaSession = MediaSessionCompat(applicationContext, "UltrasonicService")
        mediaSessionToken = mediaSession!!.sessionToken
        //mediaController = new MediaControllerCompat(getApplicationContext(), mediaSessionToken);
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                play()
                Timber.w("Media Session Callback: onPlay")
            }

            override fun onPause() {
                super.onPause()
                pause()
                Timber.w("Media Session Callback: onPause")
            }

            override fun onStop() {
                super.onStop()
                stop()
                Timber.w("Media Session Callback: onStop")
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                // This probably won't be necessary once we implement more
                // of the modern media APIs, like the MediaController etc.
                val event = mediaButtonEvent.extras!!["android.intent.extra.KEY_EVENT"] as KeyEvent?
                val lifecycleSupport = mediaPlayerLifecycleSupport.value
                lifecycleSupport.handleKeyEvent(event)
                return true
            }
        }
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
        private const val NOTIFICATION_ID = 3033
        private var instance: MediaPlayerService? = null
        private val instanceLock = Any()
        @JvmStatic
        fun getInstance(context: Context): MediaPlayerService? {
            synchronized(instanceLock) {
                for (i in 0..19) {
                    if (instance != null) return instance
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(Intent(context, MediaPlayerService::class.java))
                    } else {
                        context.startService(Intent(context, MediaPlayerService::class.java))
                    }
                    Util.sleepQuietly(50L)
                }
                return instance
            }
        }

        @JvmStatic
        val runningInstance: MediaPlayerService?
            get() {
                synchronized(instanceLock) { return instance }
            }

        @JvmStatic
        fun executeOnStartedMediaPlayerService(context: Context, taskToExecute: Consumer<MediaPlayerService?>) {
            val t: Thread = object : Thread() {
                override fun run() {
                    val instance = getInstance(context)
                    if (instance == null) {
                        Timber.e("ExecuteOnStartedMediaPlayerService failed to get a MediaPlayerService instance!")
                        return
                    }
                    taskToExecute.accept(instance)
                }
            }
            t.start()
        }
    }
}