package org.moire.ultrasonic.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import org.moire.ultrasonic.cache.AndroidDirectories
import org.moire.ultrasonic.cache.Directories
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.NowPlayingEventDistributor
import org.moire.ultrasonic.util.PermissionUtil
import org.moire.ultrasonic.util.ThemeChangedEventDistributor

val applicationModule = module {
    single { ActiveServerProvider(get(), androidContext()) }
    single { ImageLoaderProvider(androidContext()) }
    single { PermissionUtil(androidContext()) }
    single { NowPlayingEventDistributor() }
    single { ThemeChangedEventDistributor() }
}
