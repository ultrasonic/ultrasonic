package org.moire.ultrasonic.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.moire.ultrasonic.service.AudioFocusHandler
import org.moire.ultrasonic.service.DownloadQueueSerializer
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.service.ExternalStorageMonitor
import org.moire.ultrasonic.service.JukeboxMediaPlayer
import org.moire.ultrasonic.service.LocalMediaPlayer
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.util.ShufflePlayBuffer

/**
 * This Koin module contains the registration of classes related to the media player
 */
val mediaPlayerModule = module {
    single { JukeboxMediaPlayer(get()) }
    single { MediaPlayerLifecycleSupport(get(), get(), get()) }
    single { DownloadQueueSerializer(androidContext()) }
    single { ExternalStorageMonitor() }
    single { ShufflePlayBuffer() }
    single { Downloader(get(), get(), get()) }
    single { LocalMediaPlayer(get(), androidContext()) }
    single { AudioFocusHandler(get()) }

    // TODO Ideally this can be cleaned up when all circular references are removed.
    single { MediaPlayerController(get(), get(), get(), get(), get()) }
}
