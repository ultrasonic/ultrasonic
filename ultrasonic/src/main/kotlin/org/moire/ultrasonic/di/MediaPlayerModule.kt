package org.moire.ultrasonic.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module.module
import org.moire.ultrasonic.service.*
import org.moire.ultrasonic.util.ShufflePlayBuffer

val mediaPlayerModule = module {
    single<MediaPlayerController> { MediaPlayerControllerImpl(androidContext(), get(), get(), get(), get(), get()) }
    single { JukeboxMediaPlayer(androidContext(), get()) }
    single { MediaPlayerLifecycleSupport(androidContext(), get(), get(), get()) }
    single { DownloadQueueSerializer(androidContext()) }
    single { ExternalStorageMonitor(androidContext()) }
    single { ShufflePlayBuffer(androidContext()) }
    single { Downloader(androidContext(), get(), get(), get()) }
    single { LocalMediaPlayer(androidContext()) }

    // TODO: Ideally this can be cleaned up when all circular references are removed.
    single { MediaPlayerControllerImpl(androidContext(), get(), get(), get(), get(), get()) }
}