package org.moire.ultrasonic.di

import okhttp3.OkHttpClient
import org.koin.dsl.module.applicationContext

/**
 * Provides base network dependencies.
 */
val baseNetworkModule = applicationContext {
    bean { OkHttpClient.Builder().build() }
}
