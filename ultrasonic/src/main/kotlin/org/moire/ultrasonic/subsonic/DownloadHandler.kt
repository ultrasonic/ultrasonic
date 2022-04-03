package org.moire.ultrasonic.subsonic

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.Collections
import java.util.LinkedList
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.ModalBackgroundTask
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Retrieves a list of songs and adds them to the now playing list
 */
@Suppress("LongParameterList")
class DownloadHandler(
    val mediaPlayerController: MediaPlayerController,
    val networkAndStorageChecker: NetworkAndStorageChecker
) {
    private val maxSongs = 500

    fun download(
        fragment: Fragment,
        append: Boolean,
        save: Boolean,
        autoPlay: Boolean,
        playNext: Boolean,
        shuffle: Boolean,
        songs: List<Track>,
    ) {
        val onValid = Runnable {
            // TODO: The logic here is different than in the controller...
            val insertionMode = when {
                append -> MediaPlayerController.InsertionMode.APPEND
                playNext -> MediaPlayerController.InsertionMode.AFTER_CURRENT
                else -> MediaPlayerController.InsertionMode.CLEAR
            }

            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.addToPlaylist(
                songs,
                save,
                autoPlay,
                shuffle,
                insertionMode
            )
            val playlistName: String? = fragment.arguments?.getString(
                Constants.INTENT_PLAYLIST_NAME
            )
            if (playlistName != null) {
                mediaPlayerController.suggestedPlaylistName = playlistName
            }
            if (autoPlay) {
                if (Settings.shouldTransitionOnPlayback) {
                    fragment.findNavController().popBackStack(R.id.playerFragment, true)
                    fragment.findNavController().navigate(R.id.playerFragment)
                }
            } else if (save) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned,
                        songs.size,
                        songs.size
                    )
                )
            } else if (playNext) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_play_next,
                        songs.size,
                        songs.size
                    )
                )
            } else if (append) {
                Util.toast(
                    fragment.context,
                    fragment.resources.getQuantityString(
                        R.plurals.select_album_n_songs_added,
                        songs.size,
                        songs.size
                    )
                )
            }
        }
        onValid.run()
    }

    fun downloadPlaylist(
        fragment: Fragment,
        id: String,
        name: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean
    ) {
        downloadRecursively(
            fragment,
            id,
            name,
            isShare = false,
            isDirectory = false,
            save = save,
            append = append,
            autoPlay = autoplay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = false
        )
    }

    fun downloadShare(
        fragment: Fragment,
        id: String,
        name: String?,
        save: Boolean,
        append: Boolean,
        autoplay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean
    ) {
        downloadRecursively(
            fragment,
            id,
            name,
            isShare = true,
            isDirectory = false,
            save = save,
            append = append,
            autoPlay = autoplay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = false
        )
    }

    fun downloadRecursively(
        fragment: Fragment,
        id: String?,
        save: Boolean,
        append: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean,
        isArtist: Boolean
    ) {
        if (id.isNullOrEmpty()) return
        downloadRecursively(
            fragment,
            id,
            "",
            isShare = false,
            isDirectory = true,
            save = save,
            append = append,
            autoPlay = autoPlay,
            shuffle = shuffle,
            background = background,
            playNext = playNext,
            unpin = unpin,
            isArtist = isArtist
        )
    }

    private fun downloadRecursively(
        fragment: Fragment,
        id: String,
        name: String?,
        isShare: Boolean,
        isDirectory: Boolean,
        save: Boolean,
        append: Boolean,
        autoPlay: Boolean,
        shuffle: Boolean,
        background: Boolean,
        playNext: Boolean,
        unpin: Boolean,
        isArtist: Boolean
    ) {
        val activity = fragment.activity as Activity
        val task = object : ModalBackgroundTask<List<Track>>(
            activity,
            false
        ) {

            @Throws(Throwable::class)
            override fun doInBackground(): List<Track> {
                val musicService = getMusicService()
                val songs: MutableList<Track> = LinkedList()
                val root: MusicDirectory
                if (!isOffline() && isArtist && Settings.shouldUseId3Tags) {
                    getSongsForArtist(id, songs)
                } else {
                    if (isDirectory) {
                        root = if (!isOffline() && Settings.shouldUseId3Tags)
                            musicService.getAlbum(id, name, false)
                        else
                            musicService.getMusicDirectory(id, name, false)
                    } else if (isShare) {
                        root = MusicDirectory()
                        val shares = musicService.getShares(true)
                        for (share in shares) {
                            if (share.id == id) {
                                for (entry in share.getEntries()) {
                                    root.add(entry)
                                }
                                break
                            }
                        }
                    } else {
                        root = musicService.getPlaylist(id, name!!)
                    }
                    getSongsRecursively(root, songs)
                }
                return songs
            }

            @Throws(Exception::class)
            private fun getSongsRecursively(
                parent: MusicDirectory,
                songs: MutableList<Track>
            ) {
                if (songs.size > maxSongs) {
                    return
                }
                for (song in parent.getTracks()) {
                    if (!song.isVideo) {
                        songs.add(song)
                    }
                }
                val musicService = getMusicService()
                for ((id1, _, _, title) in parent.getAlbums()) {
                    val root: MusicDirectory = if (
                        !isOffline() &&
                        Settings.shouldUseId3Tags
                    ) musicService.getAlbum(id1, title, false)
                    else musicService.getMusicDirectory(id1, title, false)
                    getSongsRecursively(root, songs)
                }
            }

            @Throws(Exception::class)
            private fun getSongsForArtist(
                id: String,
                songs: MutableCollection<Track>
            ) {
                if (songs.size > maxSongs) {
                    return
                }
                val musicService = getMusicService()
                val artist = musicService.getArtist(id, "", false)
                for ((id1) in artist) {
                    val albumDirectory = musicService.getAlbum(
                        id1,
                        "",
                        false
                    )
                    for (song in albumDirectory.getTracks()) {
                        if (!song.isVideo) {
                            songs.add(song)
                        }
                    }
                }
            }

            // Called when we have collected the tracks
            override fun done(songs: List<Track>) {
                if (Settings.shouldSortByDisc) {
                    Collections.sort(songs, EntryByDiscAndTrackComparator())
                }
                if (songs.isNotEmpty()) {
                    networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
                    if (!background) {
                        if (unpin) {
                            mediaPlayerController.unpin(songs)
                        } else {
                            val insertionMode = when {
                                append -> MediaPlayerController.InsertionMode.APPEND
                                playNext -> MediaPlayerController.InsertionMode.AFTER_CURRENT
                                else -> MediaPlayerController.InsertionMode.CLEAR
                            }
                            mediaPlayerController.addToPlaylist(
                                songs,
                                save,
                                autoPlay,
                                shuffle,
                                insertionMode
                            )
                            if (
                                !append &&
                                Settings.shouldTransitionOnPlayback
                            ) {
                                fragment.findNavController().popBackStack(
                                    R.id.playerFragment,
                                    true
                                )
                                fragment.findNavController().navigate(R.id.playerFragment)
                            }
                        }
                    } else {
                        if (unpin) {
                            mediaPlayerController.unpin(songs)
                        } else {
                            mediaPlayerController.downloadBackground(songs, save)
                        }
                    }
                }
            }
        }
        task.execute()
    }
}
