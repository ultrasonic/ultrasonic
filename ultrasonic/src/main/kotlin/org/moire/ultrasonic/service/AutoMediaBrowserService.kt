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
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.MediaSessionEventDistributor
import org.moire.ultrasonic.util.MediaSessionEventListener
import org.moire.ultrasonic.util.MediaSessionHandler
import org.moire.ultrasonic.util.Util
import timber.log.Timber

const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
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
const val MEDIA_PLAYLIST_ITEM = "MEDIA_ALBUM_ITEM"
const val MEDIA_ARTIST_ITEM = "MEDIA_ARTIST_ITEM"
const val MEDIA_ARTIST_SECTION = "MEDIA_ARTIST_SECTION"

// Currently the display limit for long lists is 100 items
const val displayLimit = 100

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
    private val musicService by lazy { MusicServiceFactory.getMusicService() }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var playlistCache: List<MusicDirectory.Entry>? = null

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
                }
            }

            override fun onPlayFromSearchRequested(query: String?, extras: Bundle?) {
                // TODO implement
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
            MEDIA_ALBUM_ID -> return getAlbums(result)
            MEDIA_PLAYLIST_ID -> return getPlaylists(result)
            MEDIA_ALBUM_FREQUENT_ID -> return getFrequentAlbums(result)
            MEDIA_ALBUM_NEWEST_ID -> return getNewestAlbums(result)
            MEDIA_ALBUM_RECENT_ID -> return getRecentAlbums(result)
            MEDIA_ALBUM_RANDOM_ID -> return getRandomAlbums(result)
            MEDIA_ALBUM_STARRED_ID -> return getStarredAlbums(result)
            MEDIA_SONG_RANDOM_ID -> return getRandomSongs(result)
            MEDIA_SONG_STARRED_ID -> return getStarredSongs(result)
            MEDIA_SHARE_ID -> return getShares(result)
            MEDIA_BOOKMARK_ID -> return getBookmarks(result)
            MEDIA_PODCAST_ID -> return getPodcasts(result)
            MEDIA_PLAYLIST_ITEM -> return getPlaylist(parentIdParts[1], parentIdParts[2], result)
            MEDIA_ARTIST_ITEM -> return getAlbums(result, parentIdParts[1])
            else -> result.sendResult(mutableListOf())
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        super.onSearch(query, extras, result)
        // TODO implement
    }

    private fun getRootItems(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

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
            var artists = if (!isOffline && useId3Tags) {
                // TODO this list can be big so we're not refreshing.
                //  Maybe a refresh menu item can be added
                musicService.getArtists(false)
            } else {
                musicService.getIndexes(musicFolderId, false)
            }

            if (section != null)
                artists = artists.filter {
                        artist -> getSectionFromName(artist.name ?: "") == section
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
                        listOf(MEDIA_ARTIST_ITEM, artist.id).joinToString("|"),
                        null
                    )
                }
            }
            result.sendResult(mediaItems)
        }
    }

    private fun getAlbums(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        artistId: String? = null
    ) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getPlaylists(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()

        serviceScope.launch {
            val playlists = musicService.getPlaylists(true)
            playlists.map { playlist ->
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
            val content = musicService.getPlaylist(id, name)

            mediaItems.add(
                R.string.select_album_play_all,
                listOf(MEDIA_PLAYLIST_ITEM, id, name).joinToString("|"),
                R.drawable.ic_stat_play_dark,
                null,
                false
            )

            // Playlist should be cached as it may contain random elements
            playlistCache = content.getAllChild()
            playlistCache!!.take(displayLimit).map { item ->
                mediaItems.add(MediaBrowserCompat.MediaItem(
                    Util.getMediaDescriptionForEntry(
                        item,
                        listOf(MEDIA_PLAYLIST_SONG_ITEM, id, name, item.id).joinToString("|")
                    ),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                ))
            }
            result.sendResult(mediaItems)
        }
    }

    private fun playPlaylist(id: String, name: String) {
        serviceScope.launch {
            if (playlistCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = musicService.getPlaylist(id, name)
                playlistCache = content.getAllChild()
            }
            mediaPlayerController.download(
                playlistCache,
                save = false,
                autoPlay = true,
                playNext = false,
                shuffle = false,
                newPlaylist = true
            )
        }
    }

    private fun playPlaylistSong(id: String, name: String, songId: String) {
        serviceScope.launch {
            if (playlistCache == null) {
                // This can only happen if Android Auto cached items, but Ultrasonic has forgot them
                val content = musicService.getPlaylist(id, name)
                playlistCache = content.getAllChild()
            }
            val song = playlistCache!!.firstOrNull{x -> x.id == songId}
            if (song != null) {
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
        }
    }

    private fun getPodcasts(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getBookmarks(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getShares(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getStarredSongs(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getRandomSongs(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getStarredAlbums(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getRandomAlbums(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getRecentAlbums(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getNewestAlbums(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun getFrequentAlbums(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        result.detach()
        result.sendResult(mediaItems)
    }

    private fun MutableList<MediaBrowserCompat.MediaItem>.add(
        title: String,
        mediaId: String,
        icon: Int?,
    ) {
        val builder = MediaDescriptionCompat.Builder()
        builder.setTitle(title)
        builder.setMediaId(mediaId)

        if (icon != null)
            builder.setIconUri(Util.getUriToDrawable(applicationContext, icon))

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

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }
}