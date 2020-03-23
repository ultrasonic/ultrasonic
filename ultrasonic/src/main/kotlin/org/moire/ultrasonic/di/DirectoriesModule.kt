package org.moire.ultrasonic.di

import org.koin.dsl.module.module
import org.moire.ultrasonic.cache.AndroidDirectories
import org.moire.ultrasonic.cache.Directories

val directoriesModule = module {
    single { AndroidDirectories(get()) } bind Directories::class
}
