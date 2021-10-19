/*
 * MediaPlayerService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.collections.ArrayList
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.RepeatMode
import org.moire.ultrasonic.imageloader.BitmapUtils
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.MediaSessionEventDistributor
import org.moire.ultrasonic.util.MediaSessionEventListener
import org.moire.ultrasonic.util.MediaSessionHandler
import org.moire.ultrasonic.util.NowPlayingEventDistributor
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShufflePlayBuffer
import org.moire.ultrasonic.util.SimpleServiceBinder
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Android Foreground Service for playing music
 * while the rest of the Ultrasonic App is in the background.
 *
 * "A foreground service is a service that the user is
 * actively aware of and isn’t a candidate for the system to kill when low on memory."
 */
@Suppress("LargeClass")
class MediaPlayerService : Service() {
    private val binder: IBinder = SimpleServiceBinder(this)
    private val scrobbler = Scrobbler()

    private val jukeboxMediaPlayer by inject<JukeboxMediaPlayer>()
    private val downloadQueueSerializer by inject<DownloadQueueSerializer>()
    private val shufflePlayBuffer by inject<ShufflePlayBuffer>()
    private val downloader by inject<Downloader>()
    private val localMediaPlayer by inject<LocalMediaPlayer>()
    private val nowPlayingEventDistributor by inject<NowPlayingEventDistributor>()
    private val mediaSessionEventDistributor by inject<MediaSessionEventDistributor>()
    private val mediaSessionHandler by inject<MediaSessionHandler>()

    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionToken: MediaSessionCompat.Token? = null
    private var isInForeground = false
    private var notificationBuilder: NotificationCompat.Builder? = null
    private lateinit var mediaSessionEventListener: MediaSessionEventListener

    private val repeatMode: RepeatMode
        get() = Settings.repeatMode

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        shufflePlayBuffer.onCreate()
        localMediaPlayer.init()

        setupOnCurrentPlayingChangedHandler()
        setupOnPlayerStateChangedHandler()
        setupOnSongCompletedHandler()

        localMediaPlayer.onPrepared = {
            downloadQueueSerializer.serializeDownloadQueue(
                downloader.playlist,
                downloader.currentPlayingIndex,
                playerPosition
            )
            null
        }

        localMediaPlayer.onNextSongRequested = Runnable { setNextPlaying() }

        mediaSessionEventListener = object : MediaSessionEventListener {
            override fun onMediaSessionTokenCreated(token: MediaSessionCompat.Token) {
                mediaSessionToken = token
            }

            override fun onSkipToQueueItemRequested(id: Long) {
                play(id.toInt())
            }
        }

        mediaSessionEventDistributor.subscribe(mediaSessionEventListener)
        mediaSessionHandler.initialize()

        // Create Notification Channel
        createNotificationChannel()

        // Update notification early. It is better to show an empty one temporarily
        // than waiting too long and letting Android kill the app
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
            mediaSessionEventDistributor.unsubscribe(mediaSessionEventListener)
            mediaSessionHandler.release()

            localMediaPlayer.release()
            downloader.stop()
            shufflePlayBuffer.onDestroy()

            mediaSession?.release()
            mediaSession = null
        } catch (ignored: Throwable) {
        }
        Timber.i("MediaPlayerService stopped")
    }

    private fun stopIfIdle() {
        synchronized(instanceLock) {
            // currentPlaying could be changed from another thread in the meantime,
            // so check again before stopping for good
            if (localMediaPlayer.currentPlaying == null ||
                localMediaPlayer.playerState === PlayerState.STOPPED
            ) {
                stopSelf()
            }
        }
    }

    fun notifyDownloaderStopped() {
        // TODO It would be nice to know if the service really can be stopped instead of just
        // checking if it is idle once...
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ stopIfIdle() }, 1000)
    }

    @Synchronized
    fun seekTo(position: Int) {
        if (jukeboxMediaPlayer.isEnabled) {
            // TODO These APIs should be more aligned
            val seconds = position / 1000
            jukeboxMediaPlayer.skip(downloader.currentPlayingIndex, seconds)
        } else {
            localMediaPlayer.seekTo(position)
        }
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            if (localMediaPlayer.playerState === PlayerState.IDLE ||
                localMediaPlayer.playerState === PlayerState.DOWNLOADING ||
                localMediaPlayer.playerState === PlayerState.PREPARING
            ) {
                return 0
            }
            return if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.positionSeconds * 1000
            } else {
                localMediaPlayer.playerPosition
            }
        }

    @get:Synchronized
    val playerDuration: Int
        get() = localMediaPlayer.playerDuration

    @Synchronized
    fun setCurrentPlaying(currentPlayingIndex: Int) {
        try {
            localMediaPlayer.setCurrentPlaying(downloader.playlist[currentPlayingIndex])
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    @Synchronized
    fun setNextPlaying() {
        val gaplessPlayback = Settings.gaplessPlayback

        if (!gaplessPlayback) {
            localMediaPlayer.clearNextPlaying(true)
            return
        }

        var index = downloader.currentPlayingIndex

        if (index != -1) {
            when (repeatMode) {
                RepeatMode.OFF -> index += 1
                RepeatMode.ALL -> index = (index + 1) % downloader.playlist.size
                RepeatMode.SINGLE -> {
                }
                else -> {
                }
            }
        }

        localMediaPlayer.clearNextPlaying(false)
        if (index < downloader.playlist.size && index != -1) {
            localMediaPlayer.setNextPlaying(downloader.playlist[index])
        } else {
            localMediaPlayer.clearNextPlaying(true)
        }
    }

    @Synchronized
    fun togglePlayPause() {
        if (localMediaPlayer.playerState === PlayerState.PAUSED ||
            localMediaPlayer.playerState === PlayerState.COMPLETED ||
            localMediaPlayer.playerState === PlayerState.STOPPED
        ) {
            start()
        } else if (localMediaPlayer.playerState === PlayerState.IDLE) {
            play()
        } else if (localMediaPlayer.playerState === PlayerState.STARTED) {
            pause()
        }
    }

    @Synchronized
    fun resumeOrPlay() {
        if (localMediaPlayer.playerState === PlayerState.PAUSED ||
            localMediaPlayer.playerState === PlayerState.COMPLETED ||
            localMediaPlayer.playerState === PlayerState.STOPPED
        ) {
            start()
        } else if (localMediaPlayer.playerState === PlayerState.IDLE) {
            play()
        }
    }

    /**
     * Plays either the current song (resume) or the first/next one in queue.
     */
    @Synchronized
    fun play() {
        val current = downloader.currentPlayingIndex
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
        if (index < 0 || index >= downloader.playlist.size) {
            resetPlayback()
        } else {
            setCurrentPlaying(index)
            if (start) {
                if (jukeboxMediaPlayer.isEnabled) {
                    jukeboxMediaPlayer.skip(index, 0)
                    localMediaPlayer.setPlayerState(PlayerState.STARTED)
                } else {
                    localMediaPlayer.play(downloader.playlist[index])
                }
            }
            downloader.checkDownloads()
            setNextPlaying()
        }
    }

    @Synchronized
    private fun resetPlayback() {
        localMediaPlayer.reset()
        localMediaPlayer.setCurrentPlaying(null)
        downloadQueueSerializer.serializeDownloadQueue(
            downloader.playlist,
            downloader.currentPlayingIndex, playerPosition
        )
    }

    @Synchronized
    fun pause() {
        if (localMediaPlayer.playerState === PlayerState.STARTED) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.stop()
            } else {
                localMediaPlayer.pause()
            }
            localMediaPlayer.setPlayerState(PlayerState.PAUSED)
        }
    }

    @Synchronized
    fun stop() {
        if (localMediaPlayer.playerState === PlayerState.STARTED) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.stop()
            } else {
                localMediaPlayer.pause()
            }
        }
        localMediaPlayer.setPlayerState(PlayerState.STOPPED)
    }

    @Synchronized
    fun start() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.start()
        } else {
            localMediaPlayer.start()
        }
        localMediaPlayer.setPlayerState(PlayerState.STARTED)
    }

    private fun updateWidget(playerState: PlayerState, song: MusicDirectory.Entry?) {
        val started = playerState === PlayerState.STARTED
        val context = this@MediaPlayerService

        UltrasonicAppWidgetProvider4X1.getInstance().notifyChange(context, song, started, false)
        UltrasonicAppWidgetProvider4X2.getInstance().notifyChange(context, song, started, true)
        UltrasonicAppWidgetProvider4X3.getInstance().notifyChange(context, song, started, false)
        UltrasonicAppWidgetProvider4X4.getInstance().notifyChange(context, song, started, false)
    }

    private fun setupOnCurrentPlayingChangedHandler() {
        localMediaPlayer.onCurrentPlayingChanged = { currentPlaying: DownloadFile? ->

            if (currentPlaying != null) {
                Util.broadcastNewTrackInfo(this@MediaPlayerService, currentPlaying.song)
                Util.broadcastA2dpMetaDataChange(
                    this@MediaPlayerService, playerPosition, currentPlaying,
                    downloader.all.size, downloader.currentPlayingIndex + 1
                )
            } else {
                Util.broadcastNewTrackInfo(this@MediaPlayerService, null)
                Util.broadcastA2dpMetaDataChange(
                    this@MediaPlayerService, playerPosition, null,
                    downloader.all.size, downloader.currentPlayingIndex + 1
                )
            }

            // Update widget
            val playerState = localMediaPlayer.playerState
            val song = currentPlaying?.song

            updateWidget(playerState, song)

            if (currentPlaying != null) {
                updateNotification(localMediaPlayer.playerState, currentPlaying)
                nowPlayingEventDistributor.raiseShowNowPlayingEvent()
            } else {
                nowPlayingEventDistributor.raiseHideNowPlayingEvent()
                stopForeground(true)
                isInForeground = false
                stopIfIdle()
            }
            null
        }
    }

    private fun setupOnPlayerStateChangedHandler() {
        localMediaPlayer.onPlayerStateChanged = {
            playerState: PlayerState,
            currentPlaying: DownloadFile?
            ->

            val context = this@MediaPlayerService

            // Notify MediaSession
            mediaSessionHandler.updateMediaSession(
                currentPlaying,
                downloader.currentPlayingIndex.toLong(),
                playerState
            )

            if (playerState === PlayerState.PAUSED) {
                downloadQueueSerializer.serializeDownloadQueue(
                    downloader.playlist, downloader.currentPlayingIndex, playerPosition
                )
            }

            val showWhenPaused = playerState !== PlayerState.STOPPED &&
                Settings.isNotificationAlwaysEnabled

            val show = playerState === PlayerState.STARTED || showWhenPaused
            val song = currentPlaying?.song

            Util.broadcastPlaybackStatusChange(context, playerState)
            Util.broadcastA2dpPlayStatusChange(
                context, playerState, song,
                downloader.playlist.size,
                downloader.playlist.indexOf(currentPlaying) + 1, playerPosition
            )

            // Update widget
            updateWidget(playerState, song)

            if (show) {
                // Only update notification if player state is one that will change the icon
                if (playerState === PlayerState.STARTED || playerState === PlayerState.PAUSED) {
                    updateNotification(playerState, currentPlaying)
                    nowPlayingEventDistributor.raiseShowNowPlayingEvent()
                }
            } else {
                nowPlayingEventDistributor.raiseHideNowPlayingEvent()
                stopForeground(true)
                isInForeground = false
                stopIfIdle()
            }

            if (playerState === PlayerState.STARTED) {
                scrobbler.scrobble(currentPlaying, false)
            } else if (playerState === PlayerState.COMPLETED) {
                scrobbler.scrobble(currentPlaying, true)
            }

            null
        }
    }

    private fun setupOnSongCompletedHandler() {
        localMediaPlayer.onSongCompleted = { currentPlaying: DownloadFile? ->
            val index = downloader.currentPlayingIndex

            if (currentPlaying != null) {
                val song = currentPlaying.song
                if (song.bookmarkPosition > 0 && Settings.shouldClearBookmark) {
                    val musicService = getMusicService()
                    try {
                        musicService.deleteBookmark(song.id)
                    } catch (ignored: Exception) {
                    }
                }
            }
            if (index != -1) {
                when (repeatMode) {
                    RepeatMode.OFF -> {
                        if (index + 1 < 0 || index + 1 >= downloader.playlist.size) {
                            if (Settings.shouldClearPlaylist) {
                                clear(true)
                                jukeboxMediaPlayer.updatePlaylist()
                            }
                            resetPlayback()
                        } else {
                            play(index + 1)
                        }
                    }
                    RepeatMode.ALL -> {
                        play((index + 1) % downloader.playlist.size)
                    }
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
        localMediaPlayer.reset()
        downloader.clearPlaylist()
        localMediaPlayer.setCurrentPlaying(null)
        setNextPlaying()
        if (serialize) {
            downloadQueueSerializer.serializeDownloadQueue(
                downloader.playlist,
                downloader.currentPlayingIndex, playerPosition
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )

            channel.lightColor = android.R.color.holo_blue_dark
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(playerState: PlayerState, currentPlaying: DownloadFile?) {
        val notification = buildForegroundNotification(playerState, currentPlaying)

        if (Settings.isNotificationEnabled) {
            if (isInForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    val manager = NotificationManagerCompat.from(this)
                    manager.notify(NOTIFICATION_ID, notification)
                }
                Timber.v("Updated notification")
            } else {
                startForeground(NOTIFICATION_ID, notification)
                isInForeground = true
                Timber.v("Created Foreground notification")
            }
        }
    }

    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    @Suppress("SpreadOperator")
    private fun buildForegroundNotification(
        playerState: PlayerState,
        currentPlaying: DownloadFile?
    ): Notification {

        // Init
        val context = applicationContext
        val song = currentPlaying?.song
        val stopIntent = Util.getPendingIntentForMediaAction(
            context,
            KeyEvent.KEYCODE_MEDIA_STOP,
            100
        )

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
            notificationBuilder!!.setContentIntent(getPendingIntentForContent())

            // This intent is executed when the user closes the notification
            notificationBuilder!!.setDeleteIntent(stopIntent)
        }

        // Use the Media Style, to enable native Android support for playback notification
        val style = androidx.media.app.NotificationCompat.MediaStyle()

        if (mediaSessionToken != null) {
            style.setMediaSession(mediaSessionToken)
        }

        // Clear old actions
        notificationBuilder!!.clearActions()

        if (song != null) {
            // Add actions
            val compactActions = addActions(context, notificationBuilder!!, playerState, song)
            // Configure shortcut actions
            style.setShowActionsInCompactView(*compactActions)
            notificationBuilder!!.setStyle(style)

            // Set song title, artist and cover
            val iconSize = (256 * context.resources.displayMetrics.density).toInt()
            val bitmap = BitmapUtils.getAlbumArtBitmapFromDisk(song, iconSize)
            notificationBuilder!!.setContentTitle(song.title)
            notificationBuilder!!.setContentText(song.artist)
            notificationBuilder!!.setLargeIcon(bitmap)
            notificationBuilder!!.setSubText(song.album)
        } else if (downloader.started) {
            // No song is playing, but Ultrasonic is downloading files
            notificationBuilder!!.setContentTitle(
                getString(R.string.notification_downloading_title)
            )
        }

        return notificationBuilder!!.build()
    }

    private fun addActions(
        context: Context,
        notificationBuilder: NotificationCompat.Builder,
        playerState: PlayerState,
        song: MusicDirectory.Entry?
    ): IntArray {
        // Init
        val compactActionList = ArrayList<Int>()
        var numActions = 0 // we start and 0 and then increment by 1 for each call to generateAction

        // Star
        if (song != null) {
            notificationBuilder.addAction(generateStarAction(context, numActions, song.starred))
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
        // notificationBuilder.setShowActionsInCompactView())
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
            2 -> // Is handled in generatePlayPauseAction()
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

        val pendingIntent = Util.getPendingIntentForMediaAction(context, keycode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun generatePlayPauseAction(
        context: Context,
        requestCode: Int,
        playerState: PlayerState
    ): NotificationCompat.Action {
        val isPlaying = playerState === PlayerState.STARTED
        val keycode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        val pendingIntent = Util.getPendingIntentForMediaAction(context, keycode, requestCode)
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

    private fun generateStarAction(
        context: Context,
        requestCode: Int,
        isStarred: Boolean
    ): NotificationCompat.Action {

        val label: String
        val icon: Int
        val keyCode: Int = KeyEvent.KEYCODE_STAR

        if (isStarred) {
            label = getString(R.string.download_menu_star)
            icon = R.drawable.ic_star_full_dark
        } else {
            label = getString(R.string.download_menu_star)
            icon = R.drawable.ic_star_hollow_dark
        }

        val pendingIntent = Util.getPendingIntentForMediaAction(context, keyCode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.putExtra(Constants.INTENT_EXTRA_NAME_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    @Suppress("MagicNumber")
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
        private const val NOTIFICATION_ID = 3033

        @Volatile
        private var instance: MediaPlayerService? = null
        private val instanceLock = Any()

        @JvmStatic
        fun getInstance(): MediaPlayerService? {
            val context = UApp.applicationContext()
            for (i in 0..19) {
                if (instance != null) return instance
                synchronized(instanceLock) {
                    if (instance != null) return instance
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(
                            Intent(context, MediaPlayerService::class.java)
                        )
                    } else {
                        context.startService(Intent(context, MediaPlayerService::class.java))
                    }
                }
                Util.sleepQuietly(100L)
            }
            return instance
        }

        @JvmStatic
        val runningInstance: MediaPlayerService?
            get() {
                synchronized(instanceLock) { return instance }
            }

        @JvmStatic
        fun executeOnStartedMediaPlayerService(
            taskToExecute: (MediaPlayerService) -> Unit
        ) {

            val t: Thread = object : Thread() {
                override fun run() {
                    val instance = getInstance()
                    if (instance == null) {
                        Timber.e("ExecuteOnStarted.. failed to get a MediaPlayerService instance!")
                        return
                    } else {
                        taskToExecute(instance)
                    }
                }
            }
            t.start()
        }
    }
}
