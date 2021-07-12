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
import timber.log.Timber


const val MY_MEDIA_ROOT_ID = "MY_MEDIA_ROOT_ID"
const val MY_MEDIA_ALBUM_ID = "MY_MEDIA_ALBUM_ID"
const val MY_MEDIA_ARTIST_ID = "MY_MEDIA_ARTIST_ID"
const val MY_MEDIA_ALBUM_ITEM = "MY_MEDIA_ALBUM_ITEM"
const val MY_MEDIA_LIBRARY_ID = "MY_MEDIA_LIBRARY_ID"
const val MY_MEDIA_PLAYLIST_ID = "MY_MEDIA_PLAYLIST_ID"

class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSessionEventListener: MediaSessionEventListener
    private val mediaSessionEventDistributor: MediaSessionEventDistributor by inject()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()

    override fun onCreate() {
        super.onCreate()

        mediaSessionEventListener = object : MediaSessionEventListener {
            override fun onMediaSessionTokenCreated(token: MediaSessionCompat.Token) {
                Timber.i("AutoMediaBrowserService onMediaSessionTokenCreated called")
                if (sessionToken == null) {
                    Timber.i("AutoMediaBrowserService onMediaSessionTokenCreated session token was null, set it to %s", token.toString())
                    sessionToken = token
                }
            }

            override fun onPlayFromMediaIdRequested(mediaId: String?, extras: Bundle?) {
                // TODO implement
                Timber.i("AutoMediaBrowserService onPlayFromMediaIdRequested called")
            }

            override fun onPlayFromSearchRequested(query: String?, extras: Bundle?) {
                // TODO implement
                Timber.i("AutoMediaBrowserService onPlayFromSearchRequested called")
            }
        }

        mediaSessionEventDistributor.subscribe(mediaSessionEventListener)

        val handler = Handler()
        handler.postDelayed({
            Timber.i("AutoMediaBrowserService starting lifecycleSupport and MediaPlayerService...")
            // TODO it seems Android Auto handles autostart, but we must check that
            lifecycleSupport.onCreate()
            MediaPlayerService.getInstance()
        }, 100)

        Timber.i("AutoMediaBrowserService onCreate called")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionEventDistributor.unsubscribe(mediaSessionEventListener)
        Timber.i("AutoMediaBrowserService onDestroy called")
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Timber.i("AutoMediaBrowserService onGetRoot called")

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
        Timber.i("AutoMediaBrowserService onLoadChildren called")

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