/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moire.ultrasonic.playback

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.CONTENT_TYPE_MUSIC
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.util.Constants

class PlaybackService : MediaLibraryService(), KoinComponent {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var dataSourceFactory: DataSource.Factory

    private val librarySessionCallback = CustomMediaLibrarySessionCallback()

    companion object {
        private const val SEARCH_QUERY_PREFIX_COMPAT = "androidx://media3-session/playFromSearch"
        private const val SEARCH_QUERY_PREFIX = "androidx://media3-session/setMediaUri"
    }

    private inner class CustomMediaLibrarySessionCallback :
        MediaLibrarySession.MediaLibrarySessionCallback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItemTree.getRootItem(),
                    params
                )
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item =
                MediaItemTree.getItem(mediaId)
                    ?: return Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    )
            return Futures.immediateFuture(LibraryResult.ofItem(item, /* params= */ null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children =
                MediaItemTree.getChildren(parentId)
                    ?: return Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    )

            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        private fun setMediaItemFromSearchQuery(query: String) {
            // Only accept query with pattern "play [Title]" or "[Title]"
            // Where [Title]: must be exactly matched
            // If no media with exact name found, play a random media instead
            val mediaTitle =
                if (query.startsWith("play ", ignoreCase = true)) {
                    query.drop(5)
                } else {
                    query
                }

            val item = MediaItemTree.getItemFromTitle(mediaTitle) ?: MediaItemTree.getRandomItem()
            player.setMediaItem(item)
        }

        override fun onSetMediaUri(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            uri: Uri,
            extras: Bundle
        ): Int {

            if (uri.toString().startsWith(SEARCH_QUERY_PREFIX) ||
                uri.toString().startsWith(SEARCH_QUERY_PREFIX_COMPAT)
            ) {
                val searchQuery =
                    uri.getQueryParameter("query")
                        ?: return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                setMediaItemFromSearchQuery(searchQuery)

                return SessionResult.RESULT_SUCCESS
            } else {
                return SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
        }
    }

    /*
     * For some reason the LocalConfiguration of MediaItem are stripped somewhere in ExoPlayer,
     * and thereby customarily it is required to rebuild it..
     */
    private class CustomMediaItemFiller : MediaSession.MediaItemFiller {
        override fun fillInLocalConfiguration(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItem: MediaItem
        ): MediaItem {
            // Again, set the Uri, so that it will get a LocalConfiguration
            val item = mediaItem.buildUpon()
                .setUri(mediaItem.mediaMetadata.mediaUri)
                .build()

            return item
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
    }

    override fun onDestroy() {
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializeSessionAndPlayer() {
        /*
        * TODO:
        *  * Could be refined to use WAKE_MODE_LOCAL when offline....
        */

        setMediaNotificationProvider(MediaNotificationProvider(UApp.applicationContext()))

        val subsonicAPIClient: SubsonicAPIClient by inject()

        // Create a MediaSource which passes calls through our OkHttp Stack
        dataSourceFactory = APIDataSource.Factory(subsonicAPIClient)

        // A download cache should not evict media, so should use a NoopCacheEvictor.
        // A download cache should not evict media, so should use a NoopCacheEvictor.
        // TODO: Add cache: https://stackoverflow.com/questions/28700391/using-cache-in-exoplayer
//        var cache = UltrasonicCache()
//
        val cacheDataSourceFactory: DataSource.Factory = CachedDataSource.Factory(dataSourceFactory)

        // Create a renderer with HW rendering support
        val renderer = DefaultRenderersFactory(this)
        renderer.setEnableAudioOffload(true)

        // Create the player
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(getAudioAttributes(), true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            // .setRenderersFactory(renderer)
            .build()

        // Enable audio offload
        // player.experimentalSetOffloadSchedulingEnabled(true)

        MediaItemTree.initialize(assets)

        // THIS Will need to use the AutoCalls
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setMediaItemFiller(CustomMediaItemFiller())
            .setSessionActivity(getPendingIntentForContent())
            .build()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(USAGE_MEDIA)
            .setContentType(CONTENT_TYPE_MUSIC)
            .build()
    }
}
