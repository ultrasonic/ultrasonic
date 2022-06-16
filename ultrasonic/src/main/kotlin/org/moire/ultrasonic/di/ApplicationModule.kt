package org.moire.ultrasonic.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * This Koin module contains the registration of general classes needed for Ultrasonic
 */
val applicationModule = module {
    single { ActiveServerProvider(get()) }
    single { ImageLoaderProvider(androidContext()) }
}
