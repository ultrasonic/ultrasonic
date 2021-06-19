package org.moire.ultrasonic.util

import android.app.Application
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.fragment.AlbumListModel
import org.moire.ultrasonic.fragment.ArtistListModel
import org.moire.ultrasonic.service.MusicServiceFactory

class AndroidAutoMediaBrowser(application: Application) {

    val albumListModel: AlbumListModel = AlbumListModel(application)
    val artistListModel: ArtistListModel = ArtistListModel(application)

    val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    var maximumRootChildLimit: Int = 4

    private val MEDIA_BROWSER_ROOT_ID = "_Ultrasonice_mb_root_"

    private val MEDIA_BROWSER_RECENT_LIST_ROOT = "_Ultrasonic_mb_recent_list_root_"
    private val MEDIA_BROWSER_ALBUM_LIST_ROOT = "_Ultrasonic_mb_album_list_root_"
    private val MEDIA_BROWSER_ARTIST_LIST_ROOT = "_Ultrasonic_mb_rtist_list_root_"

    private val MEDIA_BROWSER_RECENT_PREFIX = "_Ultrasonic_mb_recent_prefix_"
    private val MEDIA_BROWSER_ALBUM_PREFIX = "_Ultrasonic_mb_album_prefix_"
    private val MEDIA_BROWSER_ARTIST_PREFIX = "_Ultrasonic_mb_artist_prefix_"

    private val MEDIA_BROWSER_EXTRA_ALBUM_LIST = "_Ultrasonic_mb_extra_album_list_"
    private val MEDIA_BROWSER_EXTRA_MEDIA_ID = "_Ultrasonic_mb_extra_media_id_"

    class AlbumListObserver(
        val idPrefix: String,
        val result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
        data: LiveData<List<MusicDirectory.Entry>>
    ) :
        Observer<List<MusicDirectory.Entry>> {

        private var liveData: LiveData<List<MusicDirectory.Entry>>? = null

        init {
            // Order is very important here. When observerForever is called onChanged
            // will immediately be called with any past data updates. We don't care
            // about those. So by having it called *before* liveData is set will
            // signal to onChanged to ignore the first input
            data.observeForever(this)
            liveData = data
        }

        override fun onChanged(albumList: List<MusicDirectory.Entry>?) {
            if (liveData == null) {
                // See comment in the initializer
                return
            }
            liveData!!.removeObserver(this)
            if (albumList == null) {
                return
            }
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()
            for (item in albumList) {
                val entryBuilder: MediaDescriptionCompat.Builder =
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
    }

    class ArtistListObserver(
        val idPrefix: String,
        val result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
        data: LiveData<List<Artist>>
    ) :
        Observer<List<Artist>> {

        private var liveData: LiveData<List<Artist>>? = null

        init {
            // Order is very important here. When observerForever is called onChanged
            // will immediately be called with any past data updates. We don't care
            // about those. So by having it called *before* liveData is set will
            // signal to onChanged to ignore the first input
            data.observeForever(this)
            liveData = data
        }

        override fun onChanged(artistList: List<Artist>?) {
            if (liveData == null) {
                // See comment in the initializer
                return
            }
            liveData!!.removeObserver(this)
            if (artistList == null) {
                return
            }
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()
            for (item in artistList) {
                val entryBuilder: MediaDescriptionCompat.Builder =
                    MediaDescriptionCompat.Builder()
                entryBuilder
                    .setTitle(item.name)
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
    }

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
        } else if (MEDIA_BROWSER_RECENT_LIST_ROOT == parentMediaId) {
            fetchAlbumList(AlbumListType.RECENT, MEDIA_BROWSER_RECENT_PREFIX, result)
            return
        } else if (MEDIA_BROWSER_ALBUM_LIST_ROOT == parentMediaId) {
            fetchAlbumList(AlbumListType.SORTED_BY_NAME, MEDIA_BROWSER_ALBUM_PREFIX, result)
            return
        } else if (MEDIA_BROWSER_ARTIST_LIST_ROOT == parentMediaId) {
            fetchArtistList(MEDIA_BROWSER_ARTIST_PREFIX, result)
            return
        } else if (parentMediaId.startsWith(MEDIA_BROWSER_RECENT_PREFIX)) {
            fetchTrackList(parentMediaId.substring(MEDIA_BROWSER_RECENT_PREFIX.length), result)
            return
        } else if (parentMediaId.startsWith(MEDIA_BROWSER_ALBUM_PREFIX)) {
            fetchTrackList(parentMediaId.substring(MEDIA_BROWSER_ALBUM_PREFIX.length), result)
            return
        } else if (parentMediaId.startsWith(MEDIA_BROWSER_ARTIST_PREFIX)) {
            fetchArtistAlbumList(
                parentMediaId.substring(MEDIA_BROWSER_ARTIST_PREFIX.length),
                result
            )
            return
        } else {
            // Examine the passed parentMediaId to see which submenu we're at,
            // and put the children of that menu in the mediaItems list...
        }
        result.sendResult(mediaItems)
    }

    fun getBundleData(bundle: Bundle?): Pair<String, List<MusicDirectory.Entry>>? {
        if (bundle == null) {
            return null
        }

        if (!bundle.containsKey(MEDIA_BROWSER_EXTRA_ALBUM_LIST) ||
            !bundle.containsKey(MEDIA_BROWSER_EXTRA_MEDIA_ID)
        ) {
            return null
        }
        val bytes = bundle.getByteArray(MEDIA_BROWSER_EXTRA_ALBUM_LIST)
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        return Pair(
            bundle.getString(MEDIA_BROWSER_EXTRA_MEDIA_ID),
            objectInputStream.readObject() as List<MusicDirectory.Entry>
        )
    }

    private fun fetchAlbumList(
        type: AlbumListType,
        idPrefix: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        AlbumListObserver(
            idPrefix, result,
            albumListModel.albumList
        )

        val args: Bundle = Bundle()
        args.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type.toString())
        albumListModel.getAlbumList(false, null, args)
        result.detach()
    }

    private fun fetchArtistList(
        idPrefix: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        ArtistListObserver(idPrefix, result, artistListModel.artists)

        artistListModel.getItems(false, null)
        result.detach()
    }

    private fun fetchArtistAlbumList(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        executorService.execute {
            val musicService = MusicServiceFactory.getMusicService()

            val musicDirectory = musicService.getMusicDirectory(
                id, "", false
            )
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()

            for (item in musicDirectory.getAllChild()) {
                val entryBuilder: MediaDescriptionCompat.Builder =
                    MediaDescriptionCompat.Builder()
                entryBuilder.setTitle(item.title).setMediaId(MEDIA_BROWSER_ALBUM_PREFIX + item.id)
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

    private fun fetchTrackList(
        id: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        executorService.execute {
            val musicService = MusicServiceFactory.getMusicService()

            val albumDirectory = musicService.getAlbum(
                id, "", false
            )

            // The idea here is that we want to attach the full album list to every song,
            // as well as the id of the specific song. This way if someone chooses to play a song
            // we can add the song and all subsequent songs in the album
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(albumDirectory.getAllChild())
            objectOutputStream.close()
            val songList = byteArrayOutputStream.toByteArray()
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()

            for (item in albumDirectory.getAllChild()) {
                val extras = Bundle()

                extras.putByteArray(
                    MEDIA_BROWSER_EXTRA_ALBUM_LIST,
                    songList
                )
                extras.putString(
                    MEDIA_BROWSER_EXTRA_MEDIA_ID,
                    item.id
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
    }
}
