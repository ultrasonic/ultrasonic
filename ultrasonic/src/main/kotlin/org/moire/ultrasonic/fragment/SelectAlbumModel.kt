package org.moire.ultrasonic.fragment

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.LinkedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

// TODO: Break up this class into smaller more specific classes, extending a base class if necessary
@KoinApiExtension
class SelectAlbumModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val activeServerProvider: ActiveServerProvider by inject()

    private val allSongsId = "-1"

    val musicFolders: MutableLiveData<List<MusicFolder>> = MutableLiveData()
    val albumList: MutableLiveData<MusicDirectory> = MutableLiveData()
    val currentDirectory: MutableLiveData<MusicDirectory> = MutableLiveData()
    val songsForGenre: MutableLiveData<MusicDirectory> = MutableLiveData()

    var currentDirectoryIsSortable = true
    var showHeader = true
    var showSelectFolderHeader = false

    suspend fun getMusicFolders(refresh: Boolean) {
        withContext(Dispatchers.IO) {
            if (!ActiveServerProvider.isOffline()) {
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
                    parentId, name, refresh, context
                )

                val songs: MutableList<MusicDirectory.Entry> = LinkedList()
                getSongsRecursively(musicDirectory, songs)

                for (song in songs) {
                    if (!song.isDirectory) {
                        root.addChild(song)
                    }
                }
            } else {
                val musicDirectory = service.getMusicDirectory(id, name, refresh, context)

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
                root = service.getMusicDirectory(id1, title, false, context)

                getSongsRecursively(root, songs)
            }
        }
    }

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

            musicDirectory = if (allSongsId == id) {
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
                root
            } else {
                service.getAlbum(id, name, refresh)
            }
            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getSongsForGenre(genre: String, count: Int, offset: Int) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getSongsByGenre(genre, count, offset, context)
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
            currentDirectory.postValue(service.getVideos(refresh, context))
        }
    }

    suspend fun getRandom(size: Int) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getRandomSongs(size, context)

            currentDirectoryIsSortable = false
            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getPlaylist(playlistId: String, playlistName: String?) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPlaylist(playlistId, playlistName, context)

            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getPodcastEpisodes(podcastChannelId: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPodcastEpisodes(podcastChannelId, context)
            currentDirectory.postValue(musicDirectory)
        }
    }

    suspend fun getShare(shareId: String) {

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = MusicDirectory()

            val shares = service.getShares(true, context)

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

    suspend fun getAlbumList(albumListType: String, size: Int, offset: Int) {

        showHeader = false
        showSelectFolderHeader = !ActiveServerProvider.isOffline() &&
            !Util.getShouldUseId3Tags() && (
            (albumListType == AlbumListType.SORTED_BY_NAME.toString()) ||
                (albumListType == AlbumListType.SORTED_BY_ARTIST.toString())
            )

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory
            val musicFolderId = if (showSelectFolderHeader) {
                activeServerProvider.getActiveServer().musicFolderId
            } else {
                null
            }

            if (Util.getShouldUseId3Tags()) {
                musicDirectory = service.getAlbumList2(
                    albumListType, size,
                    offset, musicFolderId
                )
            } else {
                musicDirectory = service.getAlbumList(
                    albumListType, size,
                    offset, musicFolderId
                )
            }

            currentDirectoryIsSortable = sortableCollection(albumListType)
            albumList.postValue(musicDirectory)
        }
    }

    private fun sortableCollection(albumListType: String): Boolean {
        return albumListType != "newest" && albumListType != "random" &&
            albumListType != "highest" && albumListType != "recent" &&
            albumListType != "frequent"
    }

    // Returns true if the directory contains only folders
    private fun hasOnlyFolders(musicDirectory: MusicDirectory) =
        musicDirectory.getChildren(includeDirs = true, includeFiles = false).size ==
            musicDirectory.getChildren(includeDirs = true, includeFiles = true).size
}
