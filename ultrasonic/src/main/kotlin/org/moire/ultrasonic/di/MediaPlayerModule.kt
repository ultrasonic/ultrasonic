package org.moire.ultrasonic.di

import org.koin.dsl.module
import org.moire.ultrasonic.playback.LegacyPlaylistManager
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.service.ExternalStorageMonitor
import org.moire.ultrasonic.service.JukeboxMediaPlayer
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.PlaybackStateSerializer

/**
 * This Koin module contains the registration of classes related to the media player
 */
val mediaPlayerModule = module {
    single { JukeboxMediaPlayer(get()) }
    single { MediaPlayerLifecycleSupport() }
    single { PlaybackStateSerializer() }
    single { ExternalStorageMonitor() }
    single { LegacyPlaylistManager() }
    single { Downloader(get(), get()) }

    // TODO Ideally this can be cleaned up when all circular references are removed.
    single { MediaPlayerController(get(), get(), get(), get(), get()) }
}
