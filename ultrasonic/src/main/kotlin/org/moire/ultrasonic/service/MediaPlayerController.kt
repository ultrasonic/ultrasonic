/*
 * MediaPlayerController.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.RepeatMode
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerService.Companion.executeOnStartedMediaPlayerService
import org.moire.ultrasonic.service.MediaPlayerService.Companion.getInstance
import org.moire.ultrasonic.service.MediaPlayerService.Companion.runningInstance
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShufflePlayBuffer
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
    private val shufflePlayBuffer: ShufflePlayBuffer,
    private val localMediaPlayer: LocalMediaPlayer
) : KoinComponent {

    private var created = false
    var suggestedPlaylistName: String? = null
    var keepScreenOn = false
    var showVisualization = false
    private var autoPlayStart = false

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    fun onCreate() {
        if (created) return
        externalStorageMonitor.onCreate { reset() }
        isJukeboxEnabled = activeServerProvider.getActiveServer().jukeboxByDefault
        created = true
        Timber.i("MediaPlayerController created")
    }

    fun onDestroy() {
        if (!created) return
        val context = UApp.applicationContext()
        externalStorageMonitor.onDestroy()
        context.stopService(Intent(context, MediaPlayerService::class.java))
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
            save = false,
            autoPlay = false,
            playNext = false,
            shuffle = false,
            newPlaylist = newPlaylist
        )
        if (currentPlayingIndex != -1) {
            executeOnStartedMediaPlayerService { mediaPlayerService: MediaPlayerService ->
                mediaPlayerService.play(currentPlayingIndex, autoPlayStart)
                if (localMediaPlayer.currentPlaying != null) {
                    if (autoPlay && jukeboxMediaPlayer.isEnabled) {
                        jukeboxMediaPlayer.skip(
                            downloader.currentPlayingIndex,
                            currentPlayingPosition / 1000
                        )
                    } else {
                        if (localMediaPlayer.currentPlaying!!.isCompleteFileAvailable) {
                            localMediaPlayer.play(
                                localMediaPlayer.currentPlaying,
                                currentPlayingPosition,
                                autoPlay
                            )
                        }
                    }
                }
                autoPlayStart = false
            }
        }
    }

    @Synchronized
    fun preload() {
        getInstance()
    }

    @Synchronized
    fun play(index: Int) {
        executeOnStartedMediaPlayerService { service: MediaPlayerService ->
            service.play(index, true)
        }
    }

    @Synchronized
    fun play() {
        executeOnStartedMediaPlayerService { service: MediaPlayerService ->
            service.play()
        }
    }

    @Synchronized
    fun resumeOrPlay() {
        executeOnStartedMediaPlayerService { service: MediaPlayerService ->
            service.resumeOrPlay()
        }
    }

    @Synchronized
    fun togglePlayPause() {
        if (localMediaPlayer.playerState === PlayerState.IDLE) autoPlayStart = true
        executeOnStartedMediaPlayerService { service: MediaPlayerService ->
            service.togglePlayPause()
        }
    }

    @Synchronized
    fun start() {
        executeOnStartedMediaPlayerService { service: MediaPlayerService ->
            service.start()
        }
    }

    @Synchronized
    fun seekTo(position: Int) {
        val mediaPlayerService = runningInstance
        mediaPlayerService?.seekTo(position)
    }

    @Synchronized
    fun pause() {
        val mediaPlayerService = runningInstance
        mediaPlayerService?.pause()
    }

    @Synchronized
    fun stop() {
        val mediaPlayerService = runningInstance
        mediaPlayerService?.stop()
    }

    @Synchronized
    @Suppress("LongParameterList")
    fun addToPlaylist(
        songs: List<Track?>?,
        save: Boolean,
        autoPlay: Boolean,
        playNext: Boolean,
        shuffle: Boolean,
        newPlaylist: Boolean
    ) {
        if (songs == null) return
        val filteredSongs = songs.filterNotNull()
        downloader.addToPlaylist(filteredSongs, save, autoPlay, playNext, newPlaylist)
        jukeboxMediaPlayer.updatePlaylist()
        if (shuffle) shuffle()
        val isLastTrack = (downloader.getPlaylist().size - 1 == downloader.currentPlayingIndex)

        if (!playNext && !autoPlay && isLastTrack) {
            val mediaPlayerService = runningInstance
            mediaPlayerService?.setNextPlaying()
        }

        if (autoPlay) {
            play(0)
        } else {
            if (localMediaPlayer.currentPlaying == null && downloader.getPlaylist().isNotEmpty()) {
                localMediaPlayer.currentPlaying = downloader.getPlaylist()[0]
                downloader.getPlaylist()[0].setPlaying(true)
            }
            downloader.checkDownloads()
        }

        playbackStateSerializer.serialize(
            downloader.getPlaylist(),
            downloader.currentPlayingIndex,
            playerPosition
        )
    }

    @Synchronized
    fun downloadBackground(songs: List<Track?>?, save: Boolean) {
        if (songs == null) return
        val filteredSongs = songs.filterNotNull()
        downloader.downloadBackground(filteredSongs, save)
        playbackStateSerializer.serialize(
            downloader.getPlaylist(),
            downloader.currentPlayingIndex,
            playerPosition
        )
    }

    @Synchronized
    fun setCurrentPlaying(index: Int) {
        val mediaPlayerService = runningInstance
        mediaPlayerService?.setCurrentPlaying(index)
    }

    fun stopJukeboxService() {
        jukeboxMediaPlayer.stopJukeboxService()
    }

    @set:Synchronized
    var isShufflePlayEnabled: Boolean
        get() = shufflePlayBuffer.isEnabled
        set(enabled) {
            shufflePlayBuffer.isEnabled = enabled
            if (enabled) {
                clear()
                downloader.checkDownloads()
            }
        }

    @Synchronized
    fun shuffle() {
        downloader.shuffle()
        playbackStateSerializer.serialize(
            downloader.getPlaylist(),
            downloader.currentPlayingIndex,
            playerPosition
        )
        jukeboxMediaPlayer.updatePlaylist()
        val mediaPlayerService = runningInstance
        mediaPlayerService?.setNextPlaying()
    }

    @Synchronized
    fun moveItemInPlaylist(oldPos: Int, newPos: Int) {
        downloader.moveItemInPlaylist(oldPos, newPos)
    }

    @set:Synchronized
    var repeatMode: RepeatMode
        get() = Settings.repeatMode
        set(repeatMode) {
            Settings.repeatMode = repeatMode
            val mediaPlayerService = runningInstance
            mediaPlayerService?.setNextPlaying()
        }

    @Synchronized
    @JvmOverloads
    fun clear(serialize: Boolean = true) {
        val mediaPlayerService = runningInstance
        if (mediaPlayerService != null) {
            mediaPlayerService.clear(serialize)
        } else {
            // If no MediaPlayerService is available, just empty the playlist
            downloader.clearPlaylist()
            if (serialize) {
                playbackStateSerializer.serialize(
                    downloader.getPlaylist(),
                    downloader.currentPlayingIndex, playerPosition
                )
            }
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

        downloader.clearIncomplete()

        playbackStateSerializer.serialize(
            downloader.getPlaylist(),
            downloader.currentPlayingIndex,
            playerPosition
        )

        jukeboxMediaPlayer.updatePlaylist()
    }

    @Synchronized
    // TODO: If a playlist contains an item twice, this call will wrongly remove all
    fun removeFromPlaylist(downloadFile: DownloadFile) {
        if (downloadFile == localMediaPlayer.currentPlaying) {
            reset()
            currentPlaying = null
        }
        downloader.removeFromPlaylist(downloadFile)

        playbackStateSerializer.serialize(
            downloader.getPlaylist(),
            downloader.currentPlayingIndex,
            playerPosition
        )

        jukeboxMediaPlayer.updatePlaylist()

        if (downloadFile == localMediaPlayer.nextPlaying) {
            val mediaPlayerService = runningInstance
            mediaPlayerService?.setNextPlaying()
        }
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
        val index = downloader.currentPlayingIndex
        if (index == -1) {
            return
        }

        // Restart song if played more than five seconds.
        @Suppress("MagicNumber")
        if (playerPosition > 5000 || index == 0) {
            play(index)
        } else {
            play(index - 1)
        }
    }

    @Synchronized
    operator fun next() {
        val index = downloader.currentPlayingIndex
        if (index != -1) {
            when (repeatMode) {
                RepeatMode.SINGLE, RepeatMode.OFF -> {
                    // Play next if exists
                    if (index + 1 >= 0 && index + 1 < downloader.getPlaylist().size) {
                        play(index + 1)
                    }
                }
                RepeatMode.ALL -> {
                    play((index + 1) % downloader.getPlaylist().size)
                }
                else -> {
                }
            }
        }
    }

    @Synchronized
    fun reset() {
        val mediaPlayerService = runningInstance
        if (mediaPlayerService != null) localMediaPlayer.reset()
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            val mediaPlayerService = runningInstance ?: return 0
            return mediaPlayerService.playerPosition
        }

    @get:Synchronized
    val playerDuration: Int
        get() {
            val mediaPlayerService = runningInstance ?: return 0
            return mediaPlayerService.playerDuration
        }

    @set:Synchronized
    var playerState: PlayerState
        get() = localMediaPlayer.playerState
        set(state) {
            val mediaPlayerService = runningInstance
            if (mediaPlayerService != null)
                localMediaPlayer.setPlayerState(state, localMediaPlayer.currentPlaying)
        }

    @set:Synchronized
    var isJukeboxEnabled: Boolean
        get() = jukeboxMediaPlayer.isEnabled
        set(jukeboxEnabled) {
            jukeboxMediaPlayer.isEnabled = jukeboxEnabled
            playerState = PlayerState.IDLE
            if (jukeboxEnabled) {
                jukeboxMediaPlayer.startJukeboxService()
                reset()

                // Cancel current download, if necessary.
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
        if (runningInstance != null) localMediaPlayer.setVolume(volume)
    }

    private fun updateNotification() {
        runningInstance?.updateNotification(
            localMediaPlayer.playerState,
            localMediaPlayer.currentPlaying
        )
    }

    fun toggleSongStarred() {
        if (localMediaPlayer.currentPlaying == null) return
        val song = localMediaPlayer.currentPlaying!!.track

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
        localMediaPlayer.setCurrentPlaying(localMediaPlayer.currentPlaying)
        song.starred = !song.starred
    }

    @Suppress("TooGenericExceptionCaught") // The interface throws only generic exceptions
    fun setSongRating(rating: Int) {
        if (!Settings.useFiveStarRating) return
        if (localMediaPlayer.currentPlaying == null) return
        val song = localMediaPlayer.currentPlaying!!.track
        song.userRating = rating
        Thread {
            try {
                getMusicService().setRating(song.id, rating)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }.start()
        // TODO this would be better handled with a Rx command
        updateNotification()
    }

    @set:Synchronized
    var currentPlaying: DownloadFile?
        get() = localMediaPlayer.currentPlaying
        set(currentPlaying) {
            if (runningInstance != null) localMediaPlayer.setCurrentPlaying(currentPlaying)
        }

    val playlistSize: Int
        get() = downloader.getPlaylist().size

    val currentPlayingNumberOnPlaylist: Int
        get() = downloader.currentPlayingIndex

    val playList: List<DownloadFile>
        get() = downloader.getPlaylist()

    val playListDuration: Long
        get() = downloader.downloadListDuration

    fun getDownloadFileForSong(song: Track): DownloadFile {
        return downloader.getDownloadFileForSong(song)
    }

    init {
        Timber.i("MediaPlayerController constructed")
    }
}
