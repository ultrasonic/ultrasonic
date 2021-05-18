/*
 * TrackCollectionModel.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import java.util.LinkedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/*
* Model for retrieving different collections of tracks from the API
* TODO: Refactor this model to extend the GenericListModel
*/
@KoinApiExtension
class TrackCollectionModel(application: Application) : GenericListModel(application) {

    private val allSongsId = "-1"

    val currentDirectory: MutableLiveData<MusicDirectory> = MutableLiveData()
    val songsForGenre: MutableLiveData<MusicDirectory> = MutableLiveData()

    suspend fun getMusicFolders(refresh: Boolean) {
        withContext(Dispatchers.IO) {
            if (!isOffline()) {
                val musicService = MusicServiceFactory.getMusicService()
                musicFolders.postValue(musicService.getMusicFolders(refresh))
            }
        }
    }

    suspend fun getMusicDirectory(
        refresh: Boolean,
        id: String?,
        name: String?,
        parentId: String?
    ) {
        withContext(Dispatchers.IO) {

            val service = MusicServiceFactory.getMusicService()

            var root = MusicDirectory()

            if (allSongsId == id) {
                val musicDirectory = service.getMusicDirectory(
                    parentId, name, refresh
                )

                val songs: MutableList<MusicDirectory.Entry> = LinkedList()
                getSongsRecursively(musicDirectory, songs)

                for (song in songs) {
                    if (!song.isDirectory) {
                        root.addChild(song)
                    }
                }
            } else {
                val musicDirectory = service.getMusicDirectory(id, name, refresh)

                if (Util.getShouldShowAllSongsByArtist() &&
                    musicDirectory.findChild(allSongsId) == null &&
                    hasOnlyFolders(musicDirectory)
                ) {
                    val allSongs = MusicDirectory.Entry()

                    allSongs.isDirectory = true
                    allSongs.artist = name
                    allSongs.parent = id
                    allSongs.id = allSongsId
                    allSongs.title = String.format(
                        context.resources.getString(R.string.select_album_all_songs), name
                    )

                    root.addChild(allSongs)
                    root.addAll(musicDirectory.getChildren())
                } else {
                    root = musicDirectory
                }
            }

            currentDirectory.postValue(root)
        }
    }

    // Given a Music directory "songs" it recursively adds all children to "songs"
    private fun getSongsRecursively(
        parent: MusicDirectory,
        songs: MutableList<MusicDirectory.Entry>
    ) {
        val service = MusicServiceFactory.getMusicService()

        for (song in parent.getChildren(includeDirs = false, includeFiles = true)) {
            if (!song.isVideo && !song.isDirectory) {
                songs.add(song)
            }
        }

        for ((id1, _, _, title) in parent.getChildren(true, includeFiles = false)) {
            var root: MusicDirectory

            if (allSongsId != id1) {
                root = service.getMusicDirectory(id1, title, false)

                getSongsRecursively(root, songs)
            }
        }
    }

    /*
    * TODO: This method should be moved to AlbumListModel,
    * since it displays a list of albums by a specified artist.
    */
    suspend fun getArtist(refresh: Boolean, id: String?, name: String?) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()

            var root = MusicDirectory()

            val musicDirectory = service.getArtist(id, name, refresh)

            if (Util.getShouldShowAllSongsByArtist() &&
                musicDirectory.findChild(allSongsId) == null &&
                hasOnlyFolders(musicDirectory)
            ) {
                val allSongs = MusicDirectory.Entry()

                allSongs.isDirectory = true
                allSongs.artist = name
                allSongs.parent = id
                allSongs.id = allSongsId
                allSongs.title = String.format(
                    context.resources.getString(R.string.select_album_all_songs), name
                )

                root.addFirst(allSongs)
                root.addAll(musicDirectory.getChildren())
            } else {
                root = musicDirectory
            }
            currentDirectory.postValue(root)
        }
    }

    suspend fun getAlbum(refresh: Boolean, id: String?, name: String?, parentId: String?) {

        withContext(Dispatchers.IO) {

            val service = MusicServiceFactory.getMusicService()

            val musicDirectory: MusicDirectory

            if (allSongsId == id) {
                val root = MusicDirectory()

                val songs: MutableCollection<MusicDirectory.Entry> = LinkedList()
                val artist = service.getArtist(parentId, "", false)

                for ((id1) in artist.getChildren()) {
                    if (allSongsId != id1) {
                        val albumDirectory = service.getAlbum(
                            id1, "", false
                        )

                        for (song in albumDirectory.getChildren()) {
                            if (!song.isVideo) {
                                songs.add(song)
                            }
                        }
                    }
                }

                for (song in songs) {
                    if (!song.isDirectory) {
                        root.addChild(song)
                    }
                }
                musicDirectory = root
            } else {
                musicDirectory = service.getAlbum(id, name, refresh)
            }

            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getSongsForGenre(genre: String, count: Int, offset: Int) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getSongsByGenre(genre, count, offset)
            songsForGenre.postValue(musicDirectory)
        }
    }

    suspend fun getStarred() {

        withContext(Dispatchers.IO) {

            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory

            if (Util.getShouldUseId3Tags()) {
                musicDirectory = Util.getSongsFromSearchResult(service.starred2)
            } else {
                musicDirectory = Util.getSongsFromSearchResult(service.starred)
            }

            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getVideos(refresh: Boolean) {
        showHeader = false

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            currentDirectory.postValue(service.getVideos(refresh))
        }
    }

    suspend fun getRandom(size: Int) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getRandomSongs(size)

            currentListIsSortable = false
            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getPlaylist(playlistId: String, playlistName: String?) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPlaylist(playlistId, playlistName)

            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getPodcastEpisodes(podcastChannelId: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPodcastEpisodes(podcastChannelId)
            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getShare(shareId: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = MusicDirectory()

            val shares = service.getShares(true)

            for (share in shares) {
                if (share.id == shareId) {
                    for (entry in share.getEntries()) {
                        musicDirectory.addChild(entry)
                    }
                    break
                }
            }
            currentDirectory.postValue(musicDirectory)
        }
    }

    // Returns true if the directory contains only folders
    private fun hasOnlyFolders(musicDirectory: MusicDirectory) =
        musicDirectory.getChildren(includeDirs = true, includeFiles = false).size ==
            musicDirectory.getChildren(includeDirs = true, includeFiles = true).size

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        // See To_Do at the top
    }
}
