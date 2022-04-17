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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.CONTENT_TYPE_MUSIC
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings

class PlaybackService : MediaLibraryService(), KoinComponent {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var apiDataSource: APIDataSource.Factory

    private lateinit var librarySessionCallback: MediaLibrarySession.MediaLibrarySessionCallback

    private var rxBusSubscription = CompositeDisposable()

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
            return mediaItem.buildUpon()
                .setUri(mediaItem.mediaMetadata.mediaUri)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()

        rxBusSubscription += RxBus.activeServerChangeObservable.subscribe {
            // Update the API endpoint when the active server has changed
            val newClient: SubsonicAPIClient by inject()
            apiDataSource.setAPIClient(newClient)
        }
    }

    override fun onDestroy() {
        player.release()
        mediaLibrarySession.release()
        rxBusSubscription.dispose()
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
        apiDataSource = APIDataSource.Factory(subsonicAPIClient)
        val cacheDataSourceFactory: DataSource.Factory = CachedDataSource.Factory(apiDataSource)

        // Create a renderer with HW rendering support
        val renderer = DefaultRenderersFactory(this)

        if (Settings.useHwOffload)
            renderer.setEnableAudioOffload(true)

        // Create the player
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(getAudioAttributes(), true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setRenderersFactory(renderer)
            .build()

        // Enable audio offload
        if (Settings.useHwOffload)
            player.experimentalSetOffloadSchedulingEnabled(true)

        // Create browser interface
        librarySessionCallback = AutoMediaBrowserCallback(player)

        // This will need to use the AutoCalls
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
