@file:JvmName("MusicServiceModule")
package org.moire.ultrasonic.di

import kotlin.math.abs
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.cache.PermanentFileStorage
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.service.CachedMusicService
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.OfflineMusicService
import org.moire.ultrasonic.service.RESTMusicService
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
import org.moire.ultrasonic.util.Constants

internal const val ONLINE_MUSIC_SERVICE = "OnlineMusicService"
internal const val OFFLINE_MUSIC_SERVICE = "OfflineMusicService"

val musicServiceModule = module {

    single(named("ServerInstance")) {
        return@single ActiveServerProvider.getActiveServerId(androidContext())
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
                Constants.REST_PROTOCOL_VERSION
            ),
            clientID = Constants.REST_CLIENT_ID,
            allowSelfSignedCertificate = get<ActiveServerProvider>()
                .getActiveServer().allowSelfSignedCertificate,
            enableLdapUserSupport = get<ActiveServerProvider>().getActiveServer().ldapSupport,
            debug = BuildConfig.DEBUG
        )
    }

    single { SubsonicAPIClient(get()) }

    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) {
        CachedMusicService(RESTMusicService(get(), get()))
    }

    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) {
        OfflineMusicService(get(), get())
    }

    single { SubsonicImageLoader(androidContext(), get()) }
}
