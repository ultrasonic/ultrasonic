@file:JvmName("MusicServiceModule")
package org.moire.ultrasonic.di

import kotlin.math.abs
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.log.TimberOkHttpLogger
import org.moire.ultrasonic.service.CachedMusicService
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.OfflineMusicService
import org.moire.ultrasonic.service.RESTMusicService
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.util.Constants

/**
 * This Koin module contains the registration of classes related to the Music Services
 */
internal const val ONLINE_MUSIC_SERVICE = "OnlineMusicService"
internal const val OFFLINE_MUSIC_SERVICE = "OfflineMusicService"

val musicServiceModule = module {

    single(named("ServerInstance")) {
        return@single ActiveServerProvider.getActiveServerId()
    }

    single(named("ServerID")) {
        val serverInstance = get<Int>(named("ServerInstance"))
        val serverUrl = get<ActiveServerProvider>().getActiveServer().url
        return@single abs("$serverUrl$serverInstance".hashCode()).toString()
    }

    single {
        val server = get<ActiveServerProvider>().getActiveServer()

        return@single SubsonicClientConfiguration(
            baseUrl = server.url,
            username = server.userName,
            password = server.password,
            minimalProtocolVersion = SubsonicAPIVersions.getClosestKnownClientApiVersion(
                server.minimumApiVersion
                    ?: Constants.REST_PROTOCOL_VERSION
            ),
            clientID = Constants.REST_CLIENT_ID,
            allowSelfSignedCertificate = server.allowSelfSignedCertificate,
            enableLdapUserSupport = server.ldapSupport,
            debug = BuildConfig.DEBUG,
            isRealProtocolVersion = server.minimumApiVersion != null
        )
    }

    single<HttpLoggingInterceptor.Logger> { TimberOkHttpLogger() }
    single { SubsonicAPIClient(get(), get()) }

    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) {
        CachedMusicService(RESTMusicService(get(), get()))
    }

    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) {
        OfflineMusicService()
    }

    single { ImageLoader(androidContext(), get(), ImageLoaderProvider.config) }

    single { DownloadHandler(get(), get()) }
    single { NetworkAndStorageChecker(androidContext()) }
    single { ShareHandler(androidContext()) }
}
