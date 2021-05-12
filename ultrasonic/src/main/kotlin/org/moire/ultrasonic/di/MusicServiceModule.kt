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
import org.moire.ultrasonic.cache.PermanentFileStorage
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.log.TimberOkHttpLogger
import org.moire.ultrasonic.service.ApiCallResponseChecker
import org.moire.ultrasonic.service.CachedMusicService
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.OfflineMusicService
import org.moire.ultrasonic.service.RESTMusicService
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
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
        val serverId = get<String>(named("ServerID"))
        return@single PermanentFileStorage(get(), serverId, BuildConfig.DEBUG)
    }

    single {
        return@single SubsonicClientConfiguration(
            baseUrl = get<ActiveServerProvider>().getActiveServer().url,
            username = get<ActiveServerProvider>().getActiveServer().userName,
            password = get<ActiveServerProvider>().getActiveServer().password,
            minimalProtocolVersion = SubsonicAPIVersions.getClosestKnownClientApiVersion(
                get<ActiveServerProvider>().getActiveServer().minimumApiVersion
                    ?: Constants.REST_PROTOCOL_VERSION
            ),
            clientID = Constants.REST_CLIENT_ID,
            allowSelfSignedCertificate = get<ActiveServerProvider>()
                .getActiveServer().allowSelfSignedCertificate,
            enableLdapUserSupport = get<ActiveServerProvider>().getActiveServer().ldapSupport,
            debug = BuildConfig.DEBUG
        )
    }

    single<HttpLoggingInterceptor.Logger> { TimberOkHttpLogger() }
    single { SubsonicAPIClient(get(), get()) }
    single { ApiCallResponseChecker(get(), get()) }

    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) {
        CachedMusicService(RESTMusicService(get(), get(), get(), get()))
    }

    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) {
        OfflineMusicService()
    }

    single { SubsonicImageLoader(androidContext(), get()) }

    single { DownloadHandler(get(), get()) }
    single { NetworkAndStorageChecker(androidContext()) }
    single { VideoPlayer() }
    single { ShareHandler(androidContext()) }
}
