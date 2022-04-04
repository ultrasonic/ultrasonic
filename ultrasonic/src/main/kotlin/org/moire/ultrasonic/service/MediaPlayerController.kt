/*
 * MediaPlayerController.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.playback.LegacyPlaylistManager
import org.moire.ultrasonic.playback.PlaybackService
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4
import org.moire.ultrasonic.service.DownloadService.Companion.getInstance
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * The implementation of the Media Player Controller.
 * This class contains everything that is necessary for the Application UI
 * to control the Media Player implementation.
 */
@Suppress("TooManyFunctions")
class MediaPlayerController(
    private val playbackStateSerializer: PlaybackStateSerializer,
    private val externalStorageMonitor: ExternalStorageMonitor,
    private val downloader: Downloader,
    private val legacyPlaylistManager: LegacyPlaylistManager,
    val context: Context
) : KoinComponent {

    private var created = false
    var suggestedPlaylistName: String? = null
    var keepScreenOn = false
    var showVisualization = false
    private var autoPlayStart = false

    private val scrobbler = Scrobbler()

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var sessionToken =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    private var mediaControllerFuture = MediaController.Builder(
        context,
        sessionToken
    ).buildAsync()

    var controller: MediaController? = null

    fun onCreate() {
        if (created) return
        externalStorageMonitor.onCreate { reset() }
        isJukeboxEnabled = activeServerProvider.getActiveServer().jukeboxByDefault

        mediaControllerFuture.addListener({
            controller = mediaControllerFuture.get()

            controller?.addListener(object : Player.Listener {
                /*
                 * This will be called everytime the playlist has changed.
                 */
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    legacyPlaylistManager.rebuildPlaylist(controller!!)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    translatePlaybackState(playbackState = playbackState)
                    playerStateChangedHandler()
                    publishPlaybackState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    translatePlaybackState(isPlaying = isPlaying)
                    playerStateChangedHandler()
                    publishPlaybackState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    onTrackCompleted(mediaItem)
                    legacyPlaylistManager.updateCurrentPlaying(mediaItem)
                    publishPlaybackState()
                }
            })

            // controller?.play()
        }, MoreExecutors.directExecutor())

        created = true
        Timber.i("MediaPlayerController created")
    }

    @Suppress("DEPRECATION")
    fun translatePlaybackState(
        playbackState: Int = controller?.playbackState ?: 0,
        isPlaying: Boolean = controller?.isPlaying ?: false
    ) {
        legacyPlayerState = when (playbackState) {
            STATE_BUFFERING -> PlayerState.DOWNLOADING
            Player.STATE_ENDED -> {
                PlayerState.COMPLETED
            }
            Player.STATE_IDLE -> {
                PlayerState.IDLE
            }
            Player.STATE_READY -> {
                if (isPlaying) {
                    PlayerState.STARTED
                } else {
                    PlayerState.PAUSED
                }
            }
            else -> {
                PlayerState.IDLE
            }
        }
    }

    private fun playerStateChangedHandler() {

        val playerState = legacyPlayerState
        val currentPlaying = legacyPlaylistManager.currentPlaying

        when {
            playerState === PlayerState.PAUSED -> {
                // TODO: Save playlist
//                playbackStateSerializer.serialize(
//                    downloader.getPlaylist(), downloader.currentPlayingIndex, playerPosition
//                )
            }
            playerState === PlayerState.STARTED -> {
                scrobbler.scrobble(currentPlaying, false)
            }
            playerState === PlayerState.COMPLETED -> {
                scrobbler.scrobble(currentPlaying, true)
            }
        }

        // Update widget
        if (currentPlaying != null) {
            updateWidget(playerState, currentPlaying.track)
        }

        Timber.d("Processed player state change")
    }

    private fun onTrackCompleted(mediaItem: MediaItem?) {
        // This method is called before we update the currentPlaying,
        // so in fact currentPlaying will refer to the track that has just finished.
        if (legacyPlaylistManager.currentPlaying != null) {
            val song = legacyPlaylistManager.currentPlaying!!.track
            if (song.bookmarkPosition > 0 && Settings.shouldClearBookmark) {
                val musicService = getMusicService()
                try {
                    musicService.deleteBookmark(song.id)
                } catch (ignored: Exception) {
                }
            }
        }

        // Playback has ended...
        if (mediaItem == null && Settings.shouldClearPlaylist) {
            clear(true)
            jukeboxMediaPlayer.updatePlaylist()
        }
    }

    private fun publishPlaybackState() {
        RxBus.playerStatePublisher.onNext(
            RxBus.StateWithTrack(
                state = legacyPlayerState,
                track = legacyPlaylistManager.currentPlaying,
                index = currentMediaItemIndex
            )
        )
    }

    private fun updateWidget(playerState: PlayerState, song: Track?) {
        val started = playerState === PlayerState.STARTED
        val context = UApp.applicationContext()

        UltrasonicAppWidgetProvider4X1.getInstance().notifyChange(context, song, started, false)
        UltrasonicAppWidgetProvider4X2.getInstance().notifyChange(context, song, started, true)
        UltrasonicAppWidgetProvider4X3.getInstance().notifyChange(context, song, started, false)
        UltrasonicAppWidgetProvider4X4.getInstance().notifyChange(context, song, started, false)
    }

    fun onDestroy() {
        if (!created) return
        val context = UApp.applicationContext()
        externalStorageMonitor.onDestroy()
        context.stopService(Intent(context, DownloadService::class.java))
        legacyPlaylistManager.onDestroy()
        downloader.onDestroy()
        created = false
        Timber.i("MediaPlayerController destroyed")
    }

    @Synchronized
    fun restore(
        songs: List<Track?>?,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        autoPlay: Boolean,
        newPlaylist: Boolean
    ) {
        addToPlaylist(
            songs,
            cachePermanently = false,
            autoPlay = false,
            playNext = false,
            shuffle = false,
            newPlaylist = newPlaylist
        )

        if (currentPlayingIndex != -1) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.skip(
                    currentPlayingIndex,
                    currentPlayingPosition / 1000
                )
            } else {
                seekTo(currentPlayingIndex, currentPlayingPosition)
            }

            if (autoPlay) {
                prepare()
                play()
            }

            autoPlayStart = false
        }
    }

    @Synchronized
    fun preload() {
        getInstance()
    }

    @Synchronized
    fun play(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    @Synchronized
    fun play() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.start()
        } else {
            controller?.play()
        }
    }

    @Synchronized
    fun prepare() {
        controller?.prepare()
    }

    @Synchronized
    fun resumeOrPlay() {
        controller?.play()
    }

    @Synchronized
    fun togglePlayPause() {
        if (playbackState == Player.STATE_IDLE) autoPlayStart = true
        if (controller?.isPlaying == false) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    @Synchronized
    fun seekTo(position: Int) {
        controller?.seekTo(position.toLong())
    }

    @Synchronized
    fun seekTo(index: Int, position: Int) {
        controller?.seekTo(index, position.toLong())
    }

    @Synchronized
    fun pause() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.stop()
        } else {
            controller?.pause()
        }
    }

    @Synchronized
    fun stop() {
        if (jukeboxMediaPlayer.isEnabled) {
            jukeboxMediaPlayer.stop()
        } else {
            controller?.stop()
        }
    }

    @Synchronized
    @Deprecated("Use InsertionMode Syntax")
    @Suppress("LongParameterList")
    fun addToPlaylist(
        songs: List<Track?>?,
        cachePermanently: Boolean,
        autoPlay: Boolean,
        playNext: Boolean,
        shuffle: Boolean,
        newPlaylist: Boolean
    ) {
        if (songs == null) return

        val insertionMode = when {
            newPlaylist -> InsertionMode.CLEAR
            playNext -> InsertionMode.AFTER_CURRENT
            else -> InsertionMode.APPEND
        }

        val filteredSongs = songs.filterNotNull()

        addToPlaylist(
            filteredSongs, cachePermanently, autoPlay, shuffle, insertionMode
        )
    }

    @Synchronized
    fun addToPlaylist(
        songs: List<Track>,
        cachePermanently: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode
    ) {
        var insertAt = 0

        if (insertionMode == InsertionMode.CLEAR) {
            clear()
        }

        when (insertionMode) {
            InsertionMode.CLEAR -> clear()
            InsertionMode.APPEND -> insertAt = mediaItemCount
            InsertionMode.AFTER_CURRENT -> insertAt = currentMediaItemIndex
        }

        val mediaItems: List<MediaItem> = songs.map {
            val downloadFile = downloader.getDownloadFileForSong(it)
            if (cachePermanently) downloadFile.shouldSave = true
            val result = it.toMediaItem()
            legacyPlaylistManager.addToCache(result, downloader.getDownloadFileForSong(it))
            result
        }

        controller?.addMediaItems(insertAt, mediaItems)

        jukeboxMediaPlayer.updatePlaylist()

        if (shuffle) isShufflePlayEnabled = true

        if (autoPlay) {
            prepare()
            play(0)
        } else {
            downloader.checkDownloads()
        }

        playbackStateSerializer.serialize(
            legacyPlaylistManager.playlist,
            currentMediaItemIndex,
            playerPosition
        )
    }

    @Synchronized
    fun downloadBackground(songs: List<Track?>?, save: Boolean) {
        if (songs == null) return
        val filteredSongs = songs.filterNotNull()
        downloader.downloadBackground(filteredSongs, save)

        playbackStateSerializer.serialize(
            legacyPlaylistManager.playlist,
            currentMediaItemIndex,
            playerPosition
        )
    }

    fun stopJukeboxService() {
        jukeboxMediaPlayer.stopJukeboxService()
    }

    @set:Synchronized
    var isShufflePlayEnabled: Boolean
        get() = controller?.shuffleModeEnabled == true
        set(enabled) {
            controller?.shuffleModeEnabled = enabled
            if (enabled) {
                downloader.checkDownloads()
            }
        }

    @Synchronized
    fun toggleShuffle() {
        isShufflePlayEnabled = !isShufflePlayEnabled
    }

    val bufferedPercentage: Int
        get() = controller?.bufferedPercentage ?: 0

    @Synchronized
    fun moveItemInPlaylist(oldPos: Int, newPos: Int) {
        controller?.moveMediaItem(oldPos, newPos)
    }

    @set:Synchronized
    var repeatMode: Int
        get() = controller?.repeatMode ?: 0
        set(newMode) {
            controller?.repeatMode = newMode
        }

    @Synchronized
    @JvmOverloads
    fun clear(serialize: Boolean = true) {

        controller?.clearMediaItems()

        if (controller != null && serialize) {
            playbackStateSerializer.serialize(
                listOf(), -1, 0
            )
        }

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    fun clearCaches() {
        downloader.clearDownloadFileCache()
    }

    @Synchronized
    fun clearIncomplete() {
        reset()

        downloader.clearActiveDownloads()
        downloader.clearBackground()

        playbackStateSerializer.serialize(
            legacyPlaylistManager.playlist,
            currentMediaItemIndex,
            playerPosition
        )

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    // FIXME
    // With the new API we can only remove by index!!
    fun removeFromPlaylist(downloadFile: DownloadFile) {

        playbackStateSerializer.serialize(
            legacyPlaylistManager.playlist,
            currentMediaItemIndex,
            playerPosition
        )

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    // TODO: Make it require not null
    fun delete(songs: List<Track?>) {
        for (song in songs.filterNotNull()) {
            downloader.getDownloadFileForSong(song).delete()
        }
    }

    @Synchronized
    // TODO: Make it require not null
    fun unpin(songs: List<Track?>) {
        for (song in songs.filterNotNull()) {
            downloader.getDownloadFileForSong(song).unpin()
        }
    }

    @Synchronized
    fun previous() {
        controller?.seekToPrevious()
    }

    @Synchronized
    operator fun next() {
        controller?.seekToNext()
    }

    @Synchronized
    fun reset() {
        controller?.clearMediaItems()
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            return if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.positionSeconds * 1000
            } else {
                controller?.currentPosition?.toInt() ?: 0
            }
        }

    @get:Synchronized
    val playerDuration: Int
        get() {
            return controller?.duration?.toInt() ?: return 0
        }

    @Deprecated("Use Controller.playbackState and Controller.isPlaying")
    @set:Synchronized
    var legacyPlayerState: PlayerState = PlayerState.IDLE

    val playbackState: Int
        get() = controller?.playbackState ?: 0

    val isPlaying: Boolean
        get() = controller?.isPlaying ?: false

    @set:Synchronized
    var isJukeboxEnabled: Boolean
        get() = jukeboxMediaPlayer.isEnabled
        set(jukeboxEnabled) {
            jukeboxMediaPlayer.isEnabled = jukeboxEnabled

            if (jukeboxEnabled) {
                jukeboxMediaPlayer.startJukeboxService()
                reset()

                // Cancel current downloads
                downloader.clearActiveDownloads()
            } else {
                jukeboxMediaPlayer.stopJukeboxService()
            }
        }

    /**
     * This function calls the music service directly and
     * therefore can't be called from the main thread
     */
    val isJukeboxAvailable: Boolean
        get() {
            try {
                val username = activeServerProvider.getActiveServer().userName
                return getMusicService().getUser(username).jukeboxRole
            } catch (all: Exception) {
                Timber.w(all, "Error getting user information")
            }
            return false
        }

    fun adjustJukeboxVolume(up: Boolean) {
        jukeboxMediaPlayer.adjustVolume(up)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume
    }

    fun toggleSongStarred() {
        if (legacyPlaylistManager.currentPlaying == null) return
        val song = legacyPlaylistManager.currentPlaying!!.track

        Thread {
            val musicService = getMusicService()
            try {
                if (song.starred) {
                    musicService.unstar(song.id, null, null)
                } else {
                    musicService.star(song.id, null, null)
                }
            } catch (all: Exception) {
                Timber.e(all)
            }
        }.start()

        // Trigger an update
        // TODO Update Metadata of MediaItem...
        // localMediaPlayer.setCurrentPlaying(localMediaPlayer.currentPlaying)
        song.starred = !song.starred
    }

    @Suppress("TooGenericExceptionCaught") // The interface throws only generic exceptions
    fun setSongRating(rating: Int) {
        if (!Settings.useFiveStarRating) return
        if (legacyPlaylistManager.currentPlaying == null) return
        val song = legacyPlaylistManager.currentPlaying!!.track
        song.userRating = rating
        Thread {
            try {
                getMusicService().setRating(song.id, rating)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }.start()
        // TODO this would be better handled with a Rx command
        // updateNotification()
    }

    val currentMediaItem: MediaItem?
        get() = controller?.currentMediaItem

    val currentMediaItemIndex: Int
        get() = controller?.currentMediaItemIndex ?: -1

    @Deprecated("Use currentMediaItem")
    val currentPlayingLegacy: DownloadFile?
        get() = legacyPlaylistManager.currentPlaying

    val mediaItemCount: Int
        get() = controller?.mediaItemCount ?: 0

    @Deprecated("Use mediaItemCount")
    val playlistSize: Int
        get() = legacyPlaylistManager.playlist.size

    @Deprecated("Use native APIs")
    val playList: List<DownloadFile>
        get() = legacyPlaylistManager.playlist

    @Deprecated("Use timeline")
    val playListDuration: Long
        get() = legacyPlaylistManager.playlistDuration

    fun getDownloadFileForSong(song: Track): DownloadFile {
        return downloader.getDownloadFileForSong(song)
    }

    init {
        Timber.i("MediaPlayerController constructed")
    }

    enum class InsertionMode {
        CLEAR, APPEND, AFTER_CURRENT
    }
}

fun Track.toMediaItem(): MediaItem {

    val filePath = FileUtil.getSongFile(this)
    val bitrate = Settings.maxBitRate
    val uri = "$id|$bitrate|$filePath"

    val metadata = MediaMetadata.Builder()
    metadata.setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setMediaUri(uri.toUri())
        .setAlbumArtist(artist)

    val mediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .setMediaMetadata(metadata.build())

    return mediaItem.build()
}
