@file:JvmName("MusicServiceModule")
package org.moire.ultrasonic.di

import android.content.SharedPreferences
import android.util.Log
import org.koin.android.ext.koin.androidContext
import kotlin.math.abs
import org.koin.dsl.module.module
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.api.subsonic.di.subsonicApiModule
import org.moire.ultrasonic.cache.PermanentFileStorage
import org.moire.ultrasonic.service.*
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ShufflePlayBuffer

internal const val MUSIC_SERVICE_CONTEXT = "CurrentMusicService"
internal const val ONLINE_MUSIC_SERVICE = "OnlineMusicService"
internal const val OFFLINE_MUSIC_SERVICE = "OfflineMusicService"
private const val DEFAULT_SERVER_INSTANCE = 1
private const val UNKNOWN_SERVER_URL = "not-exists"
private const val LOG_TAG = "MusicServiceModule"

val musicServiceModule = module(MUSIC_SERVICE_CONTEXT) {
    subsonicApiModule()

    single(name = "ServerInstance") {
        return@single get<SharedPreferences>(SP_NAME).getInt(
            Constants.PREFERENCES_KEY_SERVER_INSTANCE,
            DEFAULT_SERVER_INSTANCE
        )
    }

    single(name = "ServerID") {
        val serverInstance = get<Int>(name = "ServerInstance")
        val sp: SharedPreferences = get(SP_NAME)
        val serverUrl = sp.getString(
            Constants.PREFERENCES_KEY_SERVER_URL + serverInstance,
            null
        )
        return@single if (serverUrl == null) {
            UNKNOWN_SERVER_URL
        } else {
            abs("$serverUrl$serverInstance".hashCode()).toString()
        }
    }

    single {
        val serverId = get<String>(name = "ServerID")
        return@single PermanentFileStorage(get(), serverId, BuildConfig.DEBUG)
    }

    single {
        val instance = get<Int>(name = "ServerInstance")
        val sp: SharedPreferences = get(SP_NAME)
        val serverUrl = sp.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null)
        val username = sp.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null)
        val password = sp.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null)
        val allowSelfSignedCertificate = sp.getBoolean(
            Constants.PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + instance,
            false
        )
        val enableLdapUserSupport = sp.getBoolean(
            Constants.PREFERENCES_KEY_LDAP_SUPPORT + instance,
            false
        )

        if (serverUrl == null ||
            username == null ||
            password == null
        ) {
            Log.i(LOG_TAG, "Server credentials is not available")
            return@single SubsonicClientConfiguration(
                baseUrl = "http://localhost",
                username = "",
                password = "",
                minimalProtocolVersion = SubsonicAPIVersions.fromApiVersion(
                    Constants.REST_PROTOCOL_VERSION
                ),
                clientID = Constants.REST_CLIENT_ID,
                allowSelfSignedCertificate = allowSelfSignedCertificate,
                enableLdapUserSupport = enableLdapUserSupport,
                debug = BuildConfig.DEBUG
            )
        } else {
            return@single SubsonicClientConfiguration(
                baseUrl = serverUrl,
                username = username,
                password = password,
                minimalProtocolVersion = SubsonicAPIVersions.fromApiVersion(
                    Constants.REST_PROTOCOL_VERSION
                ),
                clientID = Constants.REST_CLIENT_ID,
                allowSelfSignedCertificate = allowSelfSignedCertificate,
                enableLdapUserSupport = enableLdapUserSupport,
                debug = BuildConfig.DEBUG
            )
        }
    }

    single { SubsonicAPIClient(get()) }

    single<MusicService>(name = ONLINE_MUSIC_SERVICE) {
        CachedMusicService(RESTMusicService(get(), get()))
    }

    single<MusicService>(name = OFFLINE_MUSIC_SERVICE) {
        OfflineMusicService(get(), get())
    }

    single { SubsonicImageLoader(getProperty(DiProperties.APP_CONTEXT), get()) }

    single<MediaPlayerController> { MediaPlayerControllerImpl(androidContext(), get(), get(), get(), get(), get()) }
    single { JukeboxMediaPlayer(androidContext(), get()) }
    single { MediaPlayerLifecycleSupport(androidContext(), get(), get(), get()) }
    single { DownloadQueueSerializer(androidContext()) }
    single { ExternalStorageMonitor(androidContext()) }
    single { ShufflePlayBuffer(androidContext()) }
    single { Downloader(androidContext(), get(), get(), get()) }
    single { LocalMediaPlayer(androidContext()) }

    // TODO: Ideally this can be cleaned up when all circular references are removed.
    single { MediaPlayerControllerImpl(androidContext(), get(), get(), get(), get(), get()) }
}
