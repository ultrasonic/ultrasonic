package org.moire.ultrasonic.di

import org.koin.dsl.module.applicationContext
import org.moire.ultrasonic.cache.AndroidDirectories
import org.moire.ultrasonic.cache.Directories

val directoriesModule = applicationContext {
    bean { AndroidDirectories(get()) } bind Directories::class
}
