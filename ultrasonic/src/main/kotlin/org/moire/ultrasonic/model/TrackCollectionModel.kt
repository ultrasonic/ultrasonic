/*
 * TrackCollectionModel.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/*
* Model for retrieving different collections of tracks from the API
*
* TODO: Remove double data keeping in currentList/currentDirectory and use the base model liveData
*  For this refactor MusicService to replace MusicDirectories with List<Album> or List<Track>
*/
class TrackCollectionModel(application: Application) : GenericListModel(application) {

    val currentDirectory: MutableLiveData<MusicDirectory> = MutableLiveData()
    val currentList: MutableLiveData<List<MusicDirectory.Entry>> = MutableLiveData()
    val songsForGenre: MutableLiveData<MusicDirectory> = MutableLiveData()

    suspend fun getMusicDirectory(
        refresh: Boolean,
        id: String,
        name: String?,
        parentId: String?
    ) {
        withContext(Dispatchers.IO) {

            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getMusicDirectory(id, name, refresh)

            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    // Given a Music directory "songs" it recursively adds all children to "songs"
    @Suppress("unused")
    private fun getSongsRecursively(
        parent: MusicDirectory,
        songs: MutableList<MusicDirectory.Entry>
    ) {
        val service = MusicServiceFactory.getMusicService()

        for (song in parent.getTracks()) {
            if (!song.isVideo && !song.isDirectory) {
                songs.add(song)
            }
        }

        for ((id1, _, _, title) in parent.getAlbums()) {
            val root: MusicDirectory = service.getMusicDirectory(id1, title, false)
            getSongsRecursively(root, songs)
        }
    }

    suspend fun getAlbum(refresh: Boolean, id: String, name: String?, parentId: String?) {

        withContext(Dispatchers.IO) {

            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory = service.getAlbum(id, name, refresh)

            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
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

            if (Settings.shouldUseId3Tags) {
                musicDirectory = Util.getSongsFromSearchResult(service.getStarred2())
            } else {
                musicDirectory = Util.getSongsFromSearchResult(service.getStarred())
            }

            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    suspend fun getVideos(refresh: Boolean) {
        showHeader = false

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val videos = service.getVideos(refresh)
            currentDirectory.postValue(videos)
            if (videos != null) {
                updateList(videos)
            }
        }
    }

    suspend fun getRandom(size: Int) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getRandomSongs(size)

            currentListIsSortable = false
            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    suspend fun getPlaylist(playlistId: String, playlistName: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPlaylist(playlistId, playlistName)

            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    suspend fun getPodcastEpisodes(podcastChannelId: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPodcastEpisodes(podcastChannelId)
            currentDirectory.postValue(musicDirectory)
            if (musicDirectory != null) {
                updateList(musicDirectory)
            }
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
                        musicDirectory.add(entry)
                    }
                    break
                }
            }
            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    suspend fun getBookmarks() {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = Util.getSongsFromBookmarks(service.getBookmarks())
            currentDirectory.postValue(musicDirectory)
            updateList(musicDirectory)
        }
    }

    private fun updateList(root: MusicDirectory) {
        currentList.postValue(root.getTracks())
    }
}
