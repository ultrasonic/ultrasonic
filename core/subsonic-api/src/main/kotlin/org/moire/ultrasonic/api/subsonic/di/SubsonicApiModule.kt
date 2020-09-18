package org.moire.ultrasonic.api.subsonic.di

import org.koin.dsl.module
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

val subsonicApiModule = module {
    single { SubsonicAPIClient(get(), get()) }
}
