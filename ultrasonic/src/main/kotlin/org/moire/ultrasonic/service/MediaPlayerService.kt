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
import android.content.ComponentName
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
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.RepeatMode
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.NowPlayingEventDistributor
import org.moire.ultrasonic.util.ShufflePlayBuffer
import org.moire.ultrasonic.util.SimpleServiceBinder
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Android Foreground Service for playing music
 * while the rest of the Ultrasonic App is in the background.
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
    private val mediaPlayerLifecycleSupport by inject<MediaPlayerLifecycleSupport>()

    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionToken: MediaSessionCompat.Token? = null
    private var isInForeground = false
    private var notificationBuilder: NotificationCompat.Builder? = null

    private val repeatMode: RepeatMode
        get() = Util.getRepeatMode()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        downloader.onCreate()
        shufflePlayBuffer.onCreate()
        localMediaPlayer.init()

        setupOnCurrentPlayingChangedHandler()
        setupOnPlayerStateChangedHandler()
        setupOnSongCompletedHandler()

        localMediaPlayer.onPrepared = {
            downloadQueueSerializer.serializeDownloadQueue(
                downloader.downloadList,
                downloader.currentPlayingIndex,
                playerPosition
            )
            null
        }

        localMediaPlayer.onNextSongRequested = Runnable { setNextPlaying() }

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
            localMediaPlayer.setCurrentPlaying(downloader.downloadList[currentPlayingIndex])
        } catch (x: IndexOutOfBoundsException) {
            // Ignored
        }
    }

    @Synchronized
    fun setNextPlaying() {
        val gaplessPlayback = Util.getGaplessPlaybackPreference()

        if (!gaplessPlayback) {
            localMediaPlayer.clearNextPlaying(true)
            return
        }

        var index = downloader.currentPlayingIndex

        if (index != -1) {
            when (repeatMode) {
                RepeatMode.OFF -> index += 1
                RepeatMode.ALL -> index = (index + 1) % downloader.downloadList.size
                RepeatMode.SINGLE -> {
                }
                else -> {
                }
            }
        }

        localMediaPlayer.clearNextPlaying(false)
        if (index < downloader.downloadList.size && index != -1) {
            localMediaPlayer.setNextPlaying(downloader.downloadList[index])
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
        if (index < 0 || index >= downloader.downloadList.size) {
            resetPlayback()
        } else {
            setCurrentPlaying(index)
            if (start) {
                if (jukeboxMediaPlayer.isEnabled) {
                    jukeboxMediaPlayer.skip(index, 0)
                    localMediaPlayer.setPlayerState(PlayerState.STARTED)
                } else {
                    localMediaPlayer.play(downloader.downloadList[index])
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
            downloader.downloadList,
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
                    downloader.downloads.size, downloader.currentPlayingIndex + 1
                )
            } else {
                Util.broadcastNewTrackInfo(this@MediaPlayerService, null)
                Util.broadcastA2dpMetaDataChange(
                    this@MediaPlayerService, playerPosition, null,
                    downloader.downloads.size, downloader.currentPlayingIndex + 1
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
            updateMediaSession(currentPlaying, playerState)

            if (playerState === PlayerState.PAUSED) {
                downloadQueueSerializer.serializeDownloadQueue(
                    downloader.downloadList, downloader.currentPlayingIndex, playerPosition
                )
            }

            val showWhenPaused = playerState !== PlayerState.STOPPED &&
                Util.isNotificationAlwaysEnabled()

            val show = playerState === PlayerState.STARTED || showWhenPaused
            val song = currentPlaying?.song

            Util.broadcastPlaybackStatusChange(context, playerState)
            Util.broadcastA2dpPlayStatusChange(
                context, playerState, song,
                downloader.downloadList.size + downloader.backgroundDownloadList.size,
                downloader.downloadList.indexOf(currentPlaying) + 1, playerPosition
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
                scrobbler.scrobble(context, currentPlaying, false)
            } else if (playerState === PlayerState.COMPLETED) {
                scrobbler.scrobble(context, currentPlaying, true)
            }

            null
        }
    }

    private fun setupOnSongCompletedHandler() {
        localMediaPlayer.onSongCompleted = { currentPlaying: DownloadFile? ->
            val index = downloader.currentPlayingIndex

            if (currentPlaying != null) {
                val song = currentPlaying.song
                if (song.bookmarkPosition > 0 && Util.getShouldClearBookmark()) {
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
                        if (index + 1 < 0 || index + 1 >= downloader.downloadList.size) {
                            if (Util.getShouldClearPlaylist()) {
                                clear(true)
                                jukeboxMediaPlayer.updatePlaylist()
                            }
                            resetPlayback()
                        } else {
                            play(index + 1)
                        }
                    }
                    RepeatMode.ALL -> {
                        play((index + 1) % downloader.downloadList.size)
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
        downloader.clear()
        localMediaPlayer.setCurrentPlaying(null)
        setNextPlaying()
        if (serialize) {
            downloadQueueSerializer.serializeDownloadQueue(
                downloader.downloadList,
                downloader.currentPlayingIndex, playerPosition
            )
        }
    }

    private fun updateMediaSession(currentPlaying: DownloadFile?, playerState: PlayerState) {
        Timber.d("Updating the MediaSession")

        if (mediaSession == null) initMediaSessions()

        // Set Metadata
        val metadata = MediaMetadataCompat.Builder()
        val context = applicationContext
        if (currentPlaying != null) {
            try {
                val song = currentPlaying.song
                val cover = FileUtil.getAlbumArtBitmap(
                    song, Util.getMinDisplayMetric(context),
                    true
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
        val state: Int
        val isActive: Boolean

        var actions: Long = PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        // Map our playerState to native PlaybackState
        // TODO: Synchronize these APIs
        when (playerState) {
            PlayerState.STARTED -> {
                state = PlaybackStateCompat.STATE_PLAYING
                isActive = true
                actions = actions or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
            }
            PlayerState.COMPLETED,
            PlayerState.STOPPED -> {
                isActive = false
                state = PlaybackStateCompat.STATE_STOPPED
            }
            PlayerState.IDLE -> {
                isActive = false
                state = PlaybackStateCompat.STATE_NONE
                actions = 0L
            }
            PlayerState.PAUSED -> {
                isActive = true
                state = PlaybackStateCompat.STATE_PAUSED
                actions = actions or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_STOP
            }
            else -> {
                // These are the states PREPARING, PREPARED & DOWNLOADING
                isActive = true
                state = PlaybackStateCompat.STATE_PAUSED
            }
        }

        playbackState.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        // Set actions
        playbackState.setActions(actions)

        // Save the playback state
        mediaSession!!.setPlaybackState(playbackState.build())

        // Set Active state
        mediaSession!!.isActive = isActive

        Timber.d("Setting the MediaSession to active = %s", isActive)
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

        if (Util.isNotificationEnabled()) {
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

        // Add actions
        val compactActions = addActions(context, notificationBuilder!!, playerState, song)

        // Configure shortcut actions
        style.setShowActionsInCompactView(*compactActions)
        notificationBuilder!!.setStyle(style)

        // Set song title, artist and cover if possible
        if (song != null) {
            val iconSize = (256 * context.resources.displayMetrics.density).toInt()
            val bitmap = FileUtil.getAlbumArtBitmap(song, iconSize, true)
            notificationBuilder!!.setContentTitle(song.title)
            notificationBuilder!!.setContentText(song.artist)
            notificationBuilder!!.setLargeIcon(bitmap)
            notificationBuilder!!.setSubText(song.album)
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

        val pendingIntent = getPendingIntentForMediaAction(context, keycode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun generatePlayPauseAction(
        context: Context,
        requestCode: Int,
        playerState: PlayerState
    ): NotificationCompat.Action {
        val isPlaying = playerState === PlayerState.STARTED
        val keycode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        val pendingIntent = getPendingIntentForMediaAction(context, keycode, requestCode)
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

        val pendingIntent = getPendingIntentForMediaAction(context, keyCode, requestCode)
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.putExtra(Constants.INTENT_EXTRA_NAME_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun getPendingIntentForMediaAction(
        context: Context,
        keycode: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(Constants.CMD_PROCESS_KEYCODE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.setPackage(context.packageName)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun initMediaSessions() {
        @Suppress("MagicNumber")
        val keycode = 110

        Timber.w("Creating media session")

        mediaSession = MediaSessionCompat(applicationContext, "UltrasonicService")
        mediaSessionToken = mediaSession!!.sessionToken

        updateMediaButtonReceiver()

        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()

                getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    keycode
                ).send()

                Timber.v("Media Session Callback: onPlay")
            }

            override fun onPause() {
                super.onPause()
                getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onPause")
            }

            override fun onStop() {
                super.onStop()
                getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_STOP,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onStop")
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onSkipToNext")
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onSkipToPrevious")
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                // This probably won't be necessary once we implement more
                // of the modern media APIs, like the MediaController etc.
                val event = mediaButtonEvent.extras!!["android.intent.extra.KEY_EVENT"] as KeyEvent?
                mediaPlayerLifecycleSupport.handleKeyEvent(event)
                return true
            }
        }
        )
    }

    fun updateMediaButtonReceiver() {
        if (Util.getMediaButtonsEnabled()) {
            registerMediaButtonEventReceiver()
        } else {
            unregisterMediaButtonEventReceiver()
        }
    }

    private fun registerMediaButtonEventReceiver() {
        val component = ComponentName(packageName, MediaButtonIntentReceiver::class.java.name)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = component

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            INTENT_CODE_MEDIA_BUTTON,
            mediaButtonIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        mediaSession?.setMediaButtonReceiver(pendingIntent)
    }

    private fun unregisterMediaButtonEventReceiver() {
        mediaSession?.setMediaButtonReceiver(null)
    }

    @Suppress("MagicNumber")
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
        private const val NOTIFICATION_ID = 3033
        private const val INTENT_CODE_MEDIA_BUTTON = 161

        private var instance: MediaPlayerService? = null
        private val instanceLock = Any()

        @JvmStatic
        fun getInstance(context: Context): MediaPlayerService? {
            synchronized(instanceLock) {
                for (i in 0..19) {
                    if (instance != null) return instance
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(
                            Intent(context, MediaPlayerService::class.java)
                        )
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
        fun executeOnStartedMediaPlayerService(
            context: Context,
            taskToExecute: (MediaPlayerService?) -> Unit
        ) {

            val t: Thread = object : Thread() {
                override fun run() {
                    val instance = getInstance(context)
                    if (instance == null) {
                        Timber.e("ExecuteOnStarted.. failed to get a MediaPlayerService instance!")
                        return
                    }
                    taskToExecute(instance)
                }
            }
            t.start()
        }
    }
}
