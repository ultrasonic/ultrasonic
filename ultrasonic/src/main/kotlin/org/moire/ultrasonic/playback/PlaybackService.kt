/*
 * PlaybackService.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

class PlaybackService : MediaLibraryService(), KoinComponent {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var apiDataSource: APIDataSource.Factory

    private lateinit var librarySessionCallback: MediaLibrarySession.MediaLibrarySessionCallback

    private var rxBusSubscription = CompositeDisposable()

    private var isStarted = false

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
        Timber.i("onCreate called")
        super.onCreate()
        initializeSessionAndPlayer()
    }

    private fun getWakeModeFlag(): Int {
        return if (ActiveServerProvider.isOffline()) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK
    }

    override fun onDestroy() {
        Timber.i("onDestroy called")
        releasePlayerAndSession()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("Stopping the playback because we were swiped away")
        releasePlayerAndSession()
    }

    private fun releasePlayerAndSession() {
        // Broadcast that the service is being shutdown
        RxBus.stopCommandPublisher.onNext(Unit)

        player.release()
        mediaLibrarySession.release()
        rxBusSubscription.dispose()
        isStarted = false
        stopForeground(true)
        stopSelf()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializeSessionAndPlayer() {
        if (isStarted) return

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
            .setWakeMode(getWakeModeFlag())
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

        // Set a listener to update the API client when the active server has changed
        rxBusSubscription += RxBus.activeServerChangeObservable.subscribe {
            val newClient: SubsonicAPIClient by inject()
            apiDataSource.setAPIClient(newClient)

            // Set the player wake mode
            player.setWakeMode(getWakeModeFlag())
        }

        // Listen to the shutdown command
        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            Timber.i("Received destroy command via Rx")
            onDestroy()
        }

        isStarted = true
    }

    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // needed starting Android 12 (S = 31)
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
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
