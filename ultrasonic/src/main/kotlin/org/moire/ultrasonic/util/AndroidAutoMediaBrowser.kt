package org.moire.ultrasonic.util

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicServiceFactory

class AndroidAutoMediaBrowser() {

    val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    var maximumRootChildLimit: Int = 4

    private val MEDIA_BROWSER_ROOT_ID = "_Ultrasonice_mb_root_"

    private val MEDIA_BROWSER_GENRE_LIST_ROOT = "_Ultrasonic_mb_genre_list_root_"
    private val MEDIA_BROWSER_RECENT_LIST_ROOT = "_Ultrasonic_mb_recent_list_root_"
    private val MEDIA_BROWSER_ALBUM_LIST_ROOT = "_Ultrasonic_mb_album_list_root_"
    private val MEDIA_BROWSER_ARTIST_LIST_ROOT = "_Ultrasonic_mb_rtist_list_root_"

    private val MEDIA_BROWSER_GENRE_PREFIX = "_Ultrasonic_mb_genre_prefix_"
    private val MEDIA_BROWSER_RECENT_PREFIX = "_Ultrasonic_mb_recent_prefix_"
    private val MEDIA_BROWSER_ALBUM_PREFIX = "_Ultrasonic_mb_album_prefix_"
    private val MEDIA_BROWSER_ARTIST_PREFIX = "_Ultrasonic_mb_artist_prefix_"

    private val MEDIA_BROWSER_EXTRA_ENTRY_BYTES = "_Ultrasonic_mb_extra_entry_bytes_"

    fun getRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
        if (rootHints != null) {
            maximumRootChildLimit = rootHints.getInt(
                MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT,
                4
            )
        }
        // opt into the root tabs (because it's gonna be non-optional
        // real soon anyway)
        val extras = Bundle()
        val TABS_OPT_IN_HINT = "android.media.browse.AUTO_TABS_OPT_IN_HINT"
        extras.putBoolean(TABS_OPT_IN_HINT, true)
        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_BROWSER_ROOT_ID, extras)
    }

    fun loadChildren(
        parentMediaId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {

        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()

        if (MEDIA_BROWSER_ROOT_ID == parentMediaId) {
            // Build the MediaItem objects for the top level,
            // and put them in the mediaItems list...

            var genreList: MediaDescriptionCompat.Builder = MediaDescriptionCompat.Builder()
            genreList.setTitle("Genre").setMediaId(MEDIA_BROWSER_GENRE_LIST_ROOT)
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    genreList.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                )
            )
            var recentList: MediaDescriptionCompat.Builder = MediaDescriptionCompat.Builder()
            recentList.setTitle("Recent").setMediaId(MEDIA_BROWSER_RECENT_LIST_ROOT)
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    recentList.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                )
            )
            var albumList: MediaDescriptionCompat.Builder = MediaDescriptionCompat.Builder()
            albumList.setTitle("Albums").setMediaId(MEDIA_BROWSER_ALBUM_LIST_ROOT)
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    albumList.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                )
            )
            var artistList: MediaDescriptionCompat.Builder = MediaDescriptionCompat.Builder()
            artistList.setTitle("Artists").setMediaId(MEDIA_BROWSER_ARTIST_LIST_ROOT)
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    artistList.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                )
            )
        } else if (MEDIA_BROWSER_GENRE_LIST_ROOT == parentMediaId) {
            fetchGenres(result)
            return
        } else if (MEDIA_BROWSER_RECENT_LIST_ROOT == parentMediaId) {
            fetchAlbumList(AlbumListType.RECENT, MEDIA_BROWSER_RECENT_PREFIX, result)
            return
        } else if (MEDIA_BROWSER_ALBUM_LIST_ROOT == parentMediaId) {
            fetchAlbumList(AlbumListType.SORTED_BY_NAME, MEDIA_BROWSER_ALBUM_PREFIX, result)
            return
        } else if (MEDIA_BROWSER_ARTIST_LIST_ROOT == parentMediaId) {
            fetchAlbumList(AlbumListType.SORTED_BY_ARTIST, MEDIA_BROWSER_ARTIST_PREFIX, result)
            return
        } else if (parentMediaId.startsWith(MEDIA_BROWSER_ALBUM_PREFIX)) {
            executorService.execute {
                val musicService = MusicServiceFactory.getMusicService()
                val id = parentMediaId.substring(MEDIA_BROWSER_ALBUM_PREFIX.length)

                val albumDirectory = musicService.getAlbum(
                    id, "", false
                )
                for (item in albumDirectory.getAllChild()) {
                    val extras = Bundle()

                    // Note that Bundle supports putSerializable and MusicDirectory.Entry
                    // implements Serializable, but when I try to use it the app crashes
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
                    objectOutputStream.writeObject(item)
                    objectOutputStream.close()
                    extras.putByteArray(
                        MEDIA_BROWSER_EXTRA_ENTRY_BYTES,
                        byteArrayOutputStream.toByteArray()
                    )

                    val entryBuilder: MediaDescriptionCompat.Builder =
                        MediaDescriptionCompat.Builder()
                    entryBuilder.setTitle(item.title).setMediaId(item.id).setExtras(extras)
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            entryBuilder.build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                result.sendResult(mediaItems)
            }
            result.detach()
            return
        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems)
    }

    fun getMusicDirectoryEntry(bundle: Bundle?): MusicDirectory.Entry? {
        if (bundle == null) {
            return null
        }

        if (!bundle.containsKey(MEDIA_BROWSER_EXTRA_ENTRY_BYTES)) {
            return null
        }
        val bytes = bundle.getByteArray(MEDIA_BROWSER_EXTRA_ENTRY_BYTES)
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        return objectInputStream.readObject() as MusicDirectory.Entry
    }

    fun fetchAlbumList(
        type: AlbumListType,
        idPrefix: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        executorService.execute {
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()
            val musicService = MusicServiceFactory.getMusicService()

            val musicDirectory: MusicDirectory = musicService.getAlbumList2(
                type.toString(), 500, 0, null
            )

            for (item in musicDirectory.getAllChild()) {
                var entryBuilder: MediaDescriptionCompat.Builder =
                    MediaDescriptionCompat.Builder()
                entryBuilder
                    .setTitle(item.title)
                    .setMediaId(idPrefix + item.id)
                mediaItems.add(
                    MediaBrowserCompat.MediaItem(
                        entryBuilder.build(),
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
                )
            }
            result.sendResult(mediaItems)
        }
        result.detach()
    }

    fun fetchGenres(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        executorService.execute {
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()
            val musicService = MusicServiceFactory.getMusicService()

            val genreList: List<Genre>? = musicService.getGenres(false)
            if (genreList != null) {
                for (genre in genreList) {
                    var entryBuilder: MediaDescriptionCompat.Builder =
                        MediaDescriptionCompat.Builder()
                    entryBuilder
                        .setTitle(genre.name)
                        .setMediaId(MEDIA_BROWSER_GENRE_PREFIX + genre.index)
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            entryBuilder.build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        )
                    )
                }
            }
            result.sendResult(mediaItems)
        }
        result.detach()
    }
}
