/*
 * LegacyPlaylist.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.service.JukeboxMediaPlayer
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.LRUCache
import timber.log.Timber

/**
 * This class keeps a legacy playlist maintained which
 * reflects the internal timeline of the Media3.Player
 */
class LegacyPlaylistManager : KoinComponent {

    private val _playlist = mutableListOf<DownloadFile>()

    @JvmField
    var currentPlaying: DownloadFile? = null

    private val mediaItemCache = LRUCache<String, DownloadFile>(1000)

    val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()
    val downloader: Downloader by inject()

    private var playlistUpdateRevision: Long = 0
        private set(value) {
            field = value
            RxBus.playlistPublisher.onNext(_playlist)
        }

    fun rebuildPlaylist(controller: MediaController) {
        _playlist.clear()

        val n = controller.mediaItemCount

        for (i in 0 until n) {
            val item = controller.getMediaItemAt(i)
            val file = mediaItemCache[item.mediaMetadata.mediaUri.toString()]
            if (file != null)
                _playlist.add(file)
        }

        playlistUpdateRevision++
    }

    fun addToCache(item: MediaItem, file: DownloadFile) {
        mediaItemCache.put(item.mediaMetadata.mediaUri.toString(), file)
    }

    fun updateCurrentPlaying(item: MediaItem?) {
        currentPlaying = mediaItemCache[item?.mediaMetadata?.mediaUri.toString()]
    }

    @Synchronized
    fun clearPlaylist() {
        _playlist.clear()
        playlistUpdateRevision++
    }

    fun onDestroy() {
        clearPlaylist()
        Timber.i("PlaylistManager destroyed")
    }

    // Public facing playlist (immutable)
    val playlist: List<DownloadFile>
        get() = _playlist

    @get:Synchronized
    val playlistDuration: Long
        get() {
            var totalDuration: Long = 0
            for (downloadFile in _playlist) {
                val song = downloadFile.track
                if (!song.isDirectory) {
                    if (song.artist != null) {
                        if (song.duration != null) {
                            totalDuration += song.duration!!.toLong()
                        }
                    }
                }
            }
            return totalDuration
        }

    /**
     * Extension function
     * Gathers the download file for a given song, and modifies shouldSave if provided.
     */
    fun Track.getDownloadFile(save: Boolean? = null): DownloadFile {
        return downloader.getDownloadFileForSong(this).apply {
            if (save != null) this.shouldSave = save
        }
    }
}
