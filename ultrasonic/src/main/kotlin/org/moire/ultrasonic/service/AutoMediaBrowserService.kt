package org.moire.ultrasonic.service

import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.util.MediaSessionEventDistributor
import org.moire.ultrasonic.util.MediaSessionEventListener
import org.moire.ultrasonic.util.MediaSessionHandler
import timber.log.Timber


const val MY_MEDIA_ROOT_ID = "MY_MEDIA_ROOT_ID"
const val MY_MEDIA_ALBUM_ID = "MY_MEDIA_ALBUM_ID"
const val MY_MEDIA_ARTIST_ID = "MY_MEDIA_ARTIST_ID"
const val MY_MEDIA_ALBUM_ITEM = "MY_MEDIA_ALBUM_ITEM"
const val MY_MEDIA_LIBRARY_ID = "MY_MEDIA_LIBRARY_ID"
const val MY_MEDIA_PLAYLIST_ID = "MY_MEDIA_PLAYLIST_ID"

class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSessionEventListener: MediaSessionEventListener
    private val mediaSessionEventDistributor by inject<MediaSessionEventDistributor>()
    private val lifecycleSupport by inject<MediaPlayerLifecycleSupport>()
    private val mediaSessionHandler by inject<MediaSessionHandler>()

    override fun onCreate() {
        super.onCreate()

        mediaSessionEventListener = object : MediaSessionEventListener {
            override fun onMediaSessionTokenCreated(token: MediaSessionCompat.Token) {
                if (sessionToken == null) {
                    sessionToken = token
                }
            }

            override fun onPlayFromMediaIdRequested(mediaId: String?, extras: Bundle?) {
                // TODO implement
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

        Timber.i("AutoMediaBrowserService onDestroy finished")
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Timber.d("AutoMediaBrowserService onGetRoot called")

        // TODO: The number of horizontal items available on the Andoid Auto screen. Check and handle.
        val maximumRootChildLimit = rootHints!!.getInt(
            MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT,
            4
        )

        // TODO: The type of the horizontal items children on the Android Auto screen. Check and handle.
        val supportedRootChildFlags = rootHints!!.getInt(
            MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )

        val extras = Bundle()
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)

        return BrowserRoot(MY_MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Timber.d("AutoMediaBrowserService onLoadChildren called")

        if (parentId == MY_MEDIA_ROOT_ID) {
            return getRootItems(result)
        } else {
            return getAlbumLists(result)
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        super.onSearch(query, extras, result)
    }

    private fun getRootItems(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

        // TODO implement this with proper texts, icons, etc
        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setTitle("Library")
                    .setMediaId(MY_MEDIA_LIBRARY_ID)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setTitle("Artists")
                    .setMediaId(MY_MEDIA_ARTIST_ID)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setTitle("Albums")
                    .setMediaId(MY_MEDIA_ALBUM_ID)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setTitle("Playlists")
                    .setMediaId(MY_MEDIA_PLAYLIST_ID)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        result.sendResult(mediaItems)
    }

    private fun getAlbumLists(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

        val description = MediaDescriptionCompat.Builder()
            .setTitle("Test")
            .setMediaId(MY_MEDIA_ALBUM_ITEM + 1)
            .build()

        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        result.sendResult(mediaItems)
    }
}