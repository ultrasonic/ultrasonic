package org.moire.ultrasonic.di

import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * This Koin module provides base network dependencies.
 */
val baseNetworkModule = module {
    single { OkHttpClient.Builder().build() }
}
