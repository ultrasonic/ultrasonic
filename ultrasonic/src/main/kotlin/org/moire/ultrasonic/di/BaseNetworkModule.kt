package org.moire.ultrasonic.di

import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Provides base network dependencies.
 */
val baseNetworkModule = module {
    single { OkHttpClient.Builder().build() }
}
