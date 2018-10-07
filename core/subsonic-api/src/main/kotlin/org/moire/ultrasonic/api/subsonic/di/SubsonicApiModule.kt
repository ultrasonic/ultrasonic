package org.moire.ultrasonic.api.subsonic.di

import org.koin.dsl.context.ModuleDefinition
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

const val SUBSONIC_API_CLIENT_CONTEXT = "SubsonicApiClientContext"

fun ModuleDefinition.subsonicApiModule() = module(SUBSONIC_API_CLIENT_CONTEXT) {
    single { SubsonicAPIClient(get(), get()) }
}
