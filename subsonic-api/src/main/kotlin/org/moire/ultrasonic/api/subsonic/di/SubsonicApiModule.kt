package org.moire.ultrasonic.api.subsonic.di

import org.koin.dsl.context.Context
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

const val SUBSONIC_API_CLIENT_CONTEXT = "SubsonicApiClientContext"

fun Context.subsonicApiModule() = context(SUBSONIC_API_CLIENT_CONTEXT) {
    bean { return@bean SubsonicAPIClient(get()) }
}
