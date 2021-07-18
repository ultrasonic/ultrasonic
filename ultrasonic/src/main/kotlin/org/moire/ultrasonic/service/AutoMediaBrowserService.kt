package org.moire.ultrasonic.service

import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.util.MediaSessionEventDistributor
import org.moire.ultrasonic.util.MediaSessionEventListener
import org.moire.ultrasonic.util.MediaSessionHandler
import org.moire.ultrasonic.util.Util
import timber.log.Timber

const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
const val MEDIA_ALBUM_PAGE_ID = "MEDIA_ALBUM_PAGE_ID"
const val MEDIA_ALBUM_NEWEST_ID = "MEDIA_ALBUM_NEWEST_ID"
const val MEDIA_ALBUM_RECENT_ID = "MEDIA_ALBUM_RECENT_ID"
const val MEDIA_ALBUM_FREQUENT_ID = "MEDIA_ALBUM_FREQUENT_ID"
const val MEDIA_ALBUM_RANDOM_ID = "MEDIA_ALBUM_RANDOM_ID"
const val MEDIA_ALBUM_STARRED_ID = "MEDIA_ALBUM_STARRED_ID"
const val MEDIA_SONG_RANDOM_ID = "MEDIA_SONG_RANDOM_ID"
const val MEDIA_SONG_STARRED_ID = "MEDIA_SONG_STARRED_ID"
const val MEDIA_ARTIST_ID = "MEDIA_ARTIST_ID"
const val MEDIA_LIBRARY_ID = "MEDIA_LIBRARY_ID"
const val MEDIA_PLAYLIST_ID = "MEDIA_PLAYLIST_ID"
const val MEDIA_SHARE_ID = "MEDIA_SHARE_ID"
const val MEDIA_BOOKMARK_ID = "MEDIA_BOOKMARK_ID"
const val MEDIA_PODCAST_ID = "MEDIA_PODCAST_ID"
const val MEDIA_ALBUM_ITEM = "MEDIA_ALBUM_ITEM"
const val MEDIA_PLAYLIST_SONG_ITEM = "MEDIA_PLAYLIST_SONG_ITEM"
const val MEDIA_PLAYLIST_ITEM = "MEDIA_PLAYLIST_ITEM"
const val MEDIA_ARTIST_ITEM = "MEDIA_ARTIST_ITEM"
const val MEDIA_ARTIST_SECTION = "MEDIA_ARTIST_SECTION"
const val MEDIA_ALBUM_SONG_ITEM = "MEDIA_ALBUM_SONG_ITEM"
const val MEDIA_SONG_STARRED_ITEM = "MEDIA_SONG_STARRED_ITEM"
const val MEDIA_SONG_RANDOM_ITEM = "MEDIA_SONG_RANDOM_ITEM"
const val MEDIA_SHARE_ITEM = "MEDIA_SHARE_ITEM"
const val MEDIA_SHARE_SONG_ITEM = "MEDIA_SHARE_SONG_ITEM"
const val MEDIA_BOOKMARK_ITEM = "MEDIA_BOOKMARK_ITEM"
const val MEDIA_PODCAST_ITEM = "MEDIA_PODCAST_ITEM"
const val MEDIA_PODCAST_EPISODE_ITEM = "MEDIA_PODCAST_EPISODE_ITEM"
const val MEDIA_SEARCH_SONG_ITEM = "MEDIA_SEARCH_SONG_ITEM"

// Currently the display limit for long lists is 100 items
const val displayLimit = 100
const val searchLimit = 10

/**
 * MediaBrowserService implementation for e.g. Android Auto
 */
class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSessionEventListener: MediaSessionEventListener
    private val mediaSessionEventDistributor by inject<MediaSessionEventDistributor>()
    private val lifecycleSupport by inject<MediaPlayerLifecycleSupport>()
    private val mediaSessionHandler by inject<MediaSessionHandler>()
    private val mediaPlayerController by inject<MediaPlayerController>()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val musicService = MusicServiceFactory.getMusicService()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var playlistCache: List<MusicDirectory.Entry>? = null
    private var starredSongsCache: List<MusicDirectory.Entry>? = null
    private var randomSongsCache: List<MusicDirectory.Entry>? = null
    private var searchSongsCache: List<MusicDirectory.Entry>? = null

    private val isOffline get() = ActiveServerProvider.isOffline()
    private val useId3Tags get() = Util.getShouldUseId3Tags()
    private val musicFolderId get() = activeServerProvider.getActiveServer().musicFolderId

    override fun onCreate() {
        super.onCreate()

        mediaSessionEventListener = object : MediaSessionEventListener {
            override fun onMediaSessionTokenCreated(token: MediaSessionCompat.Token) {
                if (sessionToken == null) {
                    sessionToken = token
                }
            }

            override fun onPlayFromMediaIdRequested(mediaId: String?, extras: Bundle?) {
                Timber.d("AutoMediaBrowserService onPlayFromMediaIdRequested called. mediaId: %s", mediaId)

                if (mediaId == null) return
                val mediaIdParts = mediaId.split('|')

                when (mediaIdParts.first()) {
                    MEDIA_PLAYLIST_ITEM -> playPlaylist(mediaIdParts[1], mediaIdParts[2])
                    MEDIA_PLAYLIST_SONG_ITEM -> playPlaylistSong(mediaIdParts[1], mediaIdParts[2], mediaIdParts[3])
                    MEDIA_ALBUM_ITEM -> playAlbum(mediaIdParts[1], mediaIdParts[2])
                    MEDIA_ALBUM_SONG_ITEM -> playAlbumSong(mediaIdParts[1], mediaIdParts[2], mediaIdParts[3])
                    MEDIA_SONG_STARRED_ID -> playStarredSongs()
                    MEDIA_SONG_STARRED_ITEM -> playStarredSong(mediaIdParts[1])
                    MEDIA_SONG_RANDOM_ID -> playRandomSongs()
                    MEDIA_SONG_RANDOM_ITEM -> playRandomSong(mediaIdParts[1])
                    MEDIA_SHARE_ITEM -> playShare(mediaIdParts[1])
                    MEDIA_SHARE_SONG_ITEM -> playShareSong(mediaIdParts[1], mediaIdParts[2])
                    MEDIA_BOOKMARK_ITEM -> playBookmark(mediaIdParts[1])
                    MEDIA_PODCAST_ITEM -> playPodcast(mediaIdParts[1])
                    MEDIA_PODCAST_EPISODE_ITEM -> playPodcastEpisode(mediaIdParts[1], mediaIdParts[2])
                    MEDIA_SEARCH_SONG_ITEM -> playSearch(mediaIdParts[1])
                }
            }

            override fun onPlayFromSearchRequested(query: String?, extras: Bundle?) {
                Timber.d("AutoMediaBrowserService onPlayFromSearchRequested query: %s", query)
                if (query.isNullOrBlank()) playRandomSongs()

                serviceScope.launch {
                    val criteria = SearchCriteria(query!!, 0, 0, displayLimit)
                    val searchResult = callWithErrorHandling { musicService.search(criteria) }

                    // Try to find the best match
                    if (searchResult != null) {
                        val song = searchResult.songs
                            .asSequence()
                            .sortedByDescending { song -> song.starred }
                            .sortedByDescending { song -> song.averageRating }
                            .sortedByDescending { song -> song.userRating }
                            .sortedByDescending { song -> song.closeness }
                            .firstOrNull()

                        if (song != null) playSong(song)
                    }
                }
            }
        }

        mediaSessionEventDistributor.subscribe(mediaSessionEventListener)
        mediaSessionHandler.initialize()

        val handler = Handler()
        handler.postDelayed({
            // Ultrasonic may be started from Android Auto. This boots up the necessary components.
            Timber.d("AutoMediaBrowserService starting lifecycleSupport and MediaPlayerService...")
            lifecycleSupport.onCreate()
            MediaPlayerService.getInstance()
        }, 100)

        Timber.i("AutoMediaBrowserService onCreate finished")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionEventDistributor.unsubscribe(mediaSessionEventListener)
        mediaSessionHandler.release()
        serviceJob.cancel()

        Timber.i("AutoMediaBrowserService onDestroy finished")
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Timber.d("AutoMediaBrowserService onGetRoot called. clientPackageName: %s; clientUid: %d", clientPackageName, clientUid)

        val extras = Bundle()
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        extras.putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)

        return BrowserRoot(MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)

        val parentIdParts = parentId.split('|')

        when (parentIdParts.first()) {
            MEDIA_ROOT_ID -> return getRootItems(result)
            MEDIA_LIBRARY_ID -> return getLibrary(result)
            MEDIA_ARTIST_ID -> return getArtists(result)
            MEDIA_ARTIST_SECTION -> return getArtists(result, parentIdParts[1])
            MEDIA_ALBUM_ID -> return getAlbums(result, AlbumListType.SORTED_BY_NAME)
            MEDIA_ALBUM_PAGE_ID -> return getAlbums(result, AlbumListType.fromName(parentIdParts[1]), parentIdParts[2].toInt())
            MEDIA_PLAYLIST_ID -> return getPlaylists(result)
            MEDIA_ALBUM_FREQUENT_ID -> return getAlbums(result, AlbumListType.FREQUENT)
            MEDIA_ALBUM_NEWEST_ID -> return getAlbums(result, AlbumListType.NEWEST)
            MEDIA_ALBUM_RECENT_ID -> return getAlbums(result, AlbumListType.RECENT)
            MEDIA_ALBUM_RANDOM_ID -> return getAlbums(result, AlbumListType.RANDOM)
            MEDIA_ALBUM_STARRED_ID -> return getAlbums(result, AlbumListType.STARRED)
            MEDIA_SONG_RANDOM_ID -> return getRandomSongs(result)
            MEDIA_SONG_STARRED_ID -> return getStarredSongs(result)
            MEDIA_SHARE_ID -> return getShares(result)
            MEDIA_BOOKMARK_ID -> return getBookmarks(result)
            MEDIA_PODCAST_ID -> return getPodcasts(result)
            MEDIA_PLAYLIST_ITEM -> return getPlaylist(parentIdParts[1], parentIdParts[2], result)
            MEDIA_ARTIST_ITEM -> return getAlbumsForArtist(result, parentIdParts[1], parentIdParts[2])
            MEDIA_ALBUM_ITEM -> return getSongsForAlbum(result, parentIdParts[1], parentIdParts[2])
            MEDIA_SHARE_ITEM -> return getSongsForShare(result, parentIdParts[1])
            MEDIA_PODCAST_ITEM -> return getPodcastEpisodes(result, parentIdParts[1])
            else -> result.sendResult(mutableListOf())
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Timber.d("AutoMediaBrowserService onSearch query: %s", query)
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val criteria = SearchCriteria(query, searchLimit, searchLimit, searchLimit)
            val searchResult = callWithErrorHandling { musicService.search(criteria) }

            // TODO Add More... button to categories
            if (searchResult != null) {
                searchResult.artists.map { artist ->
                    mediaItems.add(
                        artist.name ?: "",
                        listOf(MEDIA_ARTIST_ITEM, artist.id, artist.name).joinToString("|"),
                        null,
                        R.string.search_artists
                    )
                }

                searchResult.albums.map { album ->
                    mediaItems.add(
                        album.title ?: "",
                        listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                            .joinToString("|"),
                        null,
                        R.string.search_albums
                    )
                }

                searchSongsCache = searchResult.songs
                searchResult.songs.map { song ->
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            Util.getMediaDescriptionForEntry(
                                song,
                                listOf(MEDIA_SEARCH_SONG_ITEM, song.id).joinToString("|"),
                                R.string.search_songs
                            ),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun playSearch(id : String) {
        serviceScope.launch {
            // If there is no cache, we can't play the selected song.
            if (searchSongsCache != null) {
                val song = searchSongsCache!!.firstOrNull { x -> x.id == id }
                if (song != null) playSong(song)
            }
        }
    }

    private fun getRootItems(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

        if (!isOffline)
            mediaItems.add(
                R.string.music_library_label,
                MEDIA_LIBRARY_ID,
                R.drawable.ic_library,
                null
            )

        mediaItems.add(
            R.string.main_artists_title,
            MEDIA_ARTIST_ID,
            R.drawable.ic_artist,
            null
        )

        if (!isOffline)
            mediaItems.add(
                R.string.main_albums_title,
                MEDIA_ALBUM_ID,
                R.drawable.ic_menu_browse_dark,
                null
            )

        mediaItems.add(
            R.string.playlist_label,
            MEDIA_PLAYLIST_ID,
            R.drawable.ic_menu_playlists_dark,
            null
        )

        result.sendResult(mediaItems)
    }

    private fun getLibrary(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

        // Songs
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_SONG_RANDOM_ID,
            null,
            R.string.main_songs_title
        )

        mediaItems.add(
            R.string.main_songs_starred,
            MEDIA_SONG_STARRED_ID,
            null,
            R.string.main_songs_title
        )

        // Albums
        mediaItems.add(
            R.string.main_albums_newest,
            MEDIA_ALBUM_NEWEST_ID,
            null,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_recent,
            MEDIA_ALBUM_RECENT_ID,
            null,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_frequent,
            MEDIA_ALBUM_FREQUENT_ID,
            null,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_random,
            MEDIA_ALBUM_RANDOM_ID,
            null,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_starred,
            MEDIA_ALBUM_STARRED_ID,
            null,
            R.string.main_albums_title
        )

        // Other
        mediaItems.add(R.string.button_bar_shares, MEDIA_SHARE_ID, null, null)
        mediaItems.add(R.string.button_bar_bookmarks, MEDIA_BOOKMARK_ID, null, null)
        mediaItems.add(R.string.button_bar_podcasts, MEDIA_PODCAST_ID, null, null)

        result.sendResult(mediaItems)
    }

    private fun getArtists(result: Result<MutableList<MediaBrowserCompat.MediaItem>>, section: String? = null) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val childMediaId: String
            var artists = if (!isOffline && useId3Tags) {
                childMediaId = MEDIA_ARTIST_ITEM
                // TODO this list can be big so we're not refreshing.
                //  Maybe a refresh menu item can be added
                callWithErrorHandling { musicService.getArtists(false) }
            } else {
                // This will be handled at getSongsForAlbum, which supports navigation
                childMediaId = MEDIA_ALBUM_ITEM
                callWithErrorHandling { musicService.getIndexes(musicFolderId, false) }
            }

            if (artists != null) {
                if (section != null)
                    artists = artists.filter { artist ->
                        getSectionFromName(artist.name ?: "") == section
                    }

                // If there are too many artists, create alphabetic index of them
                if (section == null && artists.count() > displayLimit) {
                    val index = mutableListOf<String>()
                    // TODO This sort should use ignoredArticles somehow...
                    artists = artists.sortedBy { artist -> artist.name }
                    artists.map { artist ->
                        val currentSection = getSectionFromName(artist.name ?: "")
                        if (!index.contains(currentSection)) {
                            index.add(currentSection)
                            mediaItems.add(
                                currentSection,
                                listOf(MEDIA_ARTIST_SECTION, currentSection).joinToString("|"),
                                null
                            )
                        }
                    }
                } else {
                    artists.map { artist ->
                        mediaItems.add(
                            artist.name ?: "",
                            listOf(childMediaId, artist.id, artist.name).joinToString("|"),
                            null
                        )
                    }
                }
                result.sendResult(mediaItems)
            }
        }
    }

    private fun getAlbumsForArtist(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        id: String,
        name: String
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        serviceScope.launch {
            val albums = if (!isOffline && useId3Tags) {
                callWithErrorHandling { musicService.getArtist(id, name,false) }
            } else {
                callWithErrorHandling { musicService.getMusicDirectory(id, name, false) }
            }

            albums?.getAllChild()?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|"),
                    null
                )
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getSongsForAlbum(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        id: String,
        name: String
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val songs = listSongsInMusicService(id, name)

            if (songs != null) {
                if (songs.getChildren(includeDirs = true, includeFiles = false).count() == 0 &&
                    songs.getChildren(includeDirs = false, includeFiles = true).count() > 0
                )
                    mediaItems.addPlayAllItem(listOf(MEDIA_ALBUM_ITEM, id, name).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getChildren().take(displayLimit)
                items.map { item ->
                    if (item.isDirectory)
                        mediaItems.add(
                            MediaBrowserCompat.MediaItem(
                                Util.getMediaDescriptionForEntry(
                                    item,
                                    listOf(MEDIA_ALBUM_ITEM, item.id, item.name).joinToString("|")
                                ),
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                            )
                        )
                    else
                        mediaItems.add(
                            MediaBrowserCompat.MediaItem(
                                Util.getMediaDescriptionForEntry(
                                    item,
                                    listOf(
                                        MEDIA_ALBUM_SONG_ITEM,
                                        id,
                                        name,
                                        item.id
                                    ).joinToString("|")
                                ),
                                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                            )
                        )
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getAlbums(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        type: AlbumListType,
        page: Int? = null
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        serviceScope.launch {
            val offset = (page ?: 0) * displayLimit
            val albums = if (useId3Tags) {
                callWithErrorHandling { musicService.getAlbumList2(type.typeName, displayLimit, offset, null) }
            } else {
                callWithErrorHandling { musicService.getAlbumList(type.typeName, displayLimit, offset, null) }
            }

            albums?.getAllChild()?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|"),
                    null
                )
            }

            if (albums?.getAllChild()?.count() ?: 0 >= displayLimit)
                mediaItems.add(
                    R.string.search_more,
                    listOf(MEDIA_ALBUM_PAGE_ID, type.typeName, (page ?: 0) + 1).joinToString("|"),
                    R.drawable.ic_menu_forward_dark,
                    null
                )

            result.sendResult(mediaItems)
        }
    }

    private fun getPlaylists(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val playlists = callWithErrorHandling { musicService.getPlaylists(true) }
            playlists?.map { playlist ->
                mediaItems.add(
                    playlist.name,
                    listOf(MEDIA_PLAYLIST_ITEM, playlist.id, playlist.name)
                        .joinToString("|"),
                    null
                )
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getPlaylist(id: String, name: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val content = callWithErrorHandling { musicService.getPlaylist(id, name) }

            if (content != null) {
                if (content.getAllChild().count() > 1)
                    mediaItems.addPlayAllItem(
                        listOf(MEDIA_PLAYLIST_ITEM, id, name).joinToString("|")
                    )

                // Playlist should be cached as it may contain random elements
                playlistCache = content.getAllChild()
                playlistCache!!.take(displayLimit).map { item ->
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            Util.getMediaDescriptionForEntry(
                                item,
                                listOf(
                                    MEDIA_PLAYLIST_SONG_ITEM,
                                    id,
                                    name,
                                    item.id
                                ).joinToString("|")
                            ),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                result.sendResult(mediaItems)
            }
        }
    }

    private fun playPlaylist(id: String, name: String) {
        serviceScope.launch {
            if (playlistCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = callWithErrorHandling { musicService.getPlaylist(id, name) }
                playlistCache = content?.getAllChild()
            }
            if (playlistCache != null) playSongs(playlistCache)
        }
    }

    private fun playPlaylistSong(id: String, name: String, songId: String) {
        serviceScope.launch {
            if (playlistCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = callWithErrorHandling { musicService.getPlaylist(id, name) }
                playlistCache = content?.getAllChild()
            }
            val song = playlistCache?.firstOrNull{x -> x.id == songId}
            if (song != null) playSong(song)
        }
    }

    private fun playAlbum(id: String, name: String) {
        serviceScope.launch {
            val songs = listSongsInMusicService(id, name)
            if (songs != null) playSongs(songs.getAllChild())
        }
    }

    private fun playAlbumSong(id: String, name: String, songId: String) {
        serviceScope.launch {
            val songs = listSongsInMusicService(id, name)
            val song = songs?.getAllChild()?.firstOrNull{x -> x.id == songId}
            if (song != null) playSong(song)
        }
    }

    private fun getPodcasts(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        serviceScope.launch {
            val podcasts = callWithErrorHandling { musicService.getPodcastsChannels(false) }

            podcasts?.map { podcast ->
                mediaItems.add(
                    podcast.title ?: "",
                    listOf(MEDIA_PODCAST_ITEM, podcast.id).joinToString("|"),
                    null
                )
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getPodcastEpisodes(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        id: String
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        serviceScope.launch {
            val episodes = callWithErrorHandling { musicService.getPodcastEpisodes(id) }

            if (episodes != null) {
                if (episodes.getAllChild().count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_PODCAST_ITEM, id).joinToString("|"))

                episodes.getAllChild().map { episode ->
                    mediaItems.add(MediaBrowserCompat.MediaItem(
                        Util.getMediaDescriptionForEntry(
                            episode,
                            listOf(MEDIA_PODCAST_EPISODE_ITEM, id, episode.id)
                                .joinToString("|")
                        ),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    ))
                }
                result.sendResult(mediaItems)
            }
        }
    }

    private fun playPodcast(id: String) {
        serviceScope.launch {
            val episodes = callWithErrorHandling { musicService.getPodcastEpisodes(id) }
            if (episodes != null) {
                playSongs(episodes.getAllChild())
            }
        }
    }

    private fun playPodcastEpisode(id: String, episodeId: String) {
        serviceScope.launch {
            val episodes = callWithErrorHandling { musicService.getPodcastEpisodes(id) }
            if (episodes != null) {
                val selectedEpisode = episodes
                    .getAllChild()
                    .firstOrNull { episode -> episode.id == episodeId }
                if (selectedEpisode != null) playSong(selectedEpisode)
            }
        }
    }

    private fun getBookmarks(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        serviceScope.launch {
            val bookmarks = callWithErrorHandling { musicService.getBookmarks() }
            if (bookmarks != null) {
                val songs = Util.getSongsFromBookmarks(bookmarks)

                songs.getAllChild().map { song ->
                    mediaItems.add(MediaBrowserCompat.MediaItem(
                        Util.getMediaDescriptionForEntry(
                            song,
                            listOf(MEDIA_BOOKMARK_ITEM, song.id).joinToString("|")
                        ),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    ))
                }
                result.sendResult(mediaItems)
            }
        }
    }

    private fun playBookmark(id: String) {
        serviceScope.launch {
            val bookmarks = callWithErrorHandling { musicService.getBookmarks() }
            if (bookmarks != null) {
                val songs = Util.getSongsFromBookmarks(bookmarks)
                val song = songs.getAllChild().firstOrNull{song -> song.id == id}
                if (song != null) playSong(song)
            }
        }
    }

    private fun getShares(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val shares = callWithErrorHandling { musicService.getShares(false) }

            shares?.map { share ->
                mediaItems.add(
                    share.name ?: "",
                    listOf(MEDIA_SHARE_ITEM, share.id)
                        .joinToString("|"),
                    null
                )
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getSongsForShare(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        id: String
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val shares = callWithErrorHandling { musicService.getShares(false) }

            val selectedShare = shares?.firstOrNull{share -> share.id == id }
            if (selectedShare != null) {

                if (selectedShare.getEntries().count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SHARE_ITEM, id).joinToString("|"))

                selectedShare.getEntries().map { song ->
                    mediaItems.add(MediaBrowserCompat.MediaItem(
                        Util.getMediaDescriptionForEntry(
                            song,
                            listOf(MEDIA_SHARE_SONG_ITEM, id, song.id).joinToString("|")
                        ),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    ))
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun playShare(id: String) {
        serviceScope.launch {
            val shares = callWithErrorHandling { musicService.getShares(false) }
            val selectedShare = shares?.firstOrNull{share -> share.id == id }
            if (selectedShare != null) {
                playSongs(selectedShare.getEntries())
            }
        }
    }

    private fun playShareSong(id: String, songId: String) {
        serviceScope.launch {
            val shares = callWithErrorHandling { musicService.getShares(false) }
            val selectedShare = shares?.firstOrNull{share -> share.id == id }
            if (selectedShare != null) {
                val song = selectedShare.getEntries().firstOrNull{x -> x.id == songId}
                if (song != null) playSong(song)
            }
        }
    }

    private fun getStarredSongs(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val songs = listStarredSongsInMusicService()

            if (songs != null) {
                if (songs.songs.count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_STARRED_ID).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.songs.take(displayLimit)
                starredSongsCache = items
                items.map { song ->
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            Util.getMediaDescriptionForEntry(
                                song,
                                listOf(MEDIA_SONG_STARRED_ITEM, song.id).joinToString("|")
                            ),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun playStarredSongs() {
        serviceScope.launch {
            if (starredSongsCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = listStarredSongsInMusicService()
                starredSongsCache = content?.songs
            }
            if (starredSongsCache != null) playSongs(starredSongsCache)
        }
    }

    private fun playStarredSong(songId: String) {
        serviceScope.launch {
            if (starredSongsCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = listStarredSongsInMusicService()
                starredSongsCache = content?.songs
            }
            val song = starredSongsCache?.firstOrNull{x -> x.id == songId}
            if (song != null) playSong(song)
        }
    }

    private fun getRandomSongs(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val songs = callWithErrorHandling { musicService.getRandomSongs(displayLimit) }

            if (songs != null) {
                if (songs.getAllChild().count() > 1)
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getAllChild()
                randomSongsCache = items
                items.map { song ->
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            Util.getMediaDescriptionForEntry(
                                song,
                                listOf(MEDIA_SONG_RANDOM_ITEM, song.id).joinToString("|")
                            ),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun playRandomSongs() {
        serviceScope.launch {
            if (randomSongsCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                // In this case we request a new set of random songs
                val content = callWithErrorHandling { musicService.getRandomSongs(displayLimit) }
                randomSongsCache = content?.getAllChild()
            }
            if (randomSongsCache != null) playSongs(randomSongsCache)
        }
    }

    private fun playRandomSong(songId: String) {
        serviceScope.launch {
            // If there is no cache, we can't play the selected song.
            if (randomSongsCache != null) {
                val song = randomSongsCache!!.firstOrNull { x -> x.id == songId }
                if (song != null) playSong(song)
            }
        }
    }

    private fun listSongsInMusicService(id: String, name: String): MusicDirectory? {
        return if (!ActiveServerProvider.isOffline() && Util.getShouldUseId3Tags()) {
            callWithErrorHandling { musicService.getAlbum(id, name, false) }
        } else {
            callWithErrorHandling { musicService.getMusicDirectory(id, name, false) }
        }
    }

    private fun listStarredSongsInMusicService(): SearchResult? {
        return if (Util.getShouldUseId3Tags()) {
            callWithErrorHandling { musicService.getStarred2() }
        } else {
            callWithErrorHandling { musicService.getStarred() }
        }
    }

    private fun MutableList<MediaBrowserCompat.MediaItem>.add(
        title: String,
        mediaId: String,
        icon: Int?,
        groupNameId: Int? = null
    ) {
        val builder = MediaDescriptionCompat.Builder()
        builder.setTitle(title)
        builder.setMediaId(mediaId)

        if (icon != null)
            builder.setIconUri(Util.getUriToDrawable(applicationContext, icon))

        if (groupNameId != null)
            builder.setExtras(Bundle().apply { putString(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                getString(groupNameId)
            ) })

        val mediaItem = MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )

        this.add(mediaItem)
    }

    private fun MutableList<MediaBrowserCompat.MediaItem>.add(
        resId: Int,
        mediaId: String,
        icon: Int?,
        groupNameId: Int?,
        browsable: Boolean = true
    ) {
        val builder = MediaDescriptionCompat.Builder()
        builder.setTitle(getString(resId))
        builder.setMediaId(mediaId)

        if (icon != null)
            builder.setIconUri(Util.getUriToDrawable(applicationContext, icon))

        if (groupNameId != null)
            builder.setExtras(Bundle().apply { putString(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                getString(groupNameId)
            ) })

        val mediaItem = MediaBrowserCompat.MediaItem(
            builder.build(),
            if (browsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )

        this.add(mediaItem)
    }

    private fun MutableList<MediaBrowserCompat.MediaItem>.addPlayAllItem(
        mediaId: String,
    ) {
        this.add(
            R.string.select_album_play_all,
            mediaId,
            R.drawable.ic_stat_play_dark,
            null,
            false
        )
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    private fun playSongs(songs: List<MusicDirectory.Entry?>?) {
        mediaPlayerController.download(
            songs,
            save = false,
            autoPlay = true,
            playNext = false,
            shuffle = false,
            newPlaylist = true
        )
    }

    private fun playSong(song: MusicDirectory.Entry) {
        mediaPlayerController.download(
            listOf(song),
            save = false,
            autoPlay = false,
            playNext = true,
            shuffle = false,
            newPlaylist = false
        )
        mediaPlayerController.next()
    }

    private fun <T> callWithErrorHandling(function: () -> T): T? {
        // TODO Implement better error handling
        return try {
            function()
        } catch (all: Exception) {
            Timber.i(all)
            null
        }
    }
}