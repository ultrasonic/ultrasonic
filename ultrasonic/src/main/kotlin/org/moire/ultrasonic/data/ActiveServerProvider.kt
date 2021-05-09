package org.moire.ultrasonic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MusicServiceFactory.resetMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * This class can be used to retrieve the properties of the Active Server
 * It caches the settings read up from the DB to improve performance.
 */
class ActiveServerProvider(
    private val repository: ServerSettingDao,
    private val context: Context
) {
    private var cachedServer: ServerSetting? = null

    /**
     * Get the settings of the current Active Server
     * @return The Active Server Settings
     */
    fun getActiveServer(): ServerSetting {
        val serverId = getActiveServerId(context)

        if (serverId > 0) {
            if (cachedServer != null && cachedServer!!.id == serverId) return cachedServer!!

            // Ideally this is the only call where we block the thread while using the repository
            runBlocking {
                withContext(Dispatchers.IO) {
                    cachedServer = repository.findById(serverId)
                }
                Timber.d(
                    "getActiveServer retrieved from DataBase, id: $serverId; " +
                        "cachedServer: $cachedServer"
                )
            }

            if (cachedServer != null) return cachedServer!!
            setActiveServerId(context, 0)
        }

        return ServerSetting(
            id = -1,
            index = 0,
            name = context.getString(R.string.main_offline),
            url = "http://localhost",
            userName = "",
            password = "",
            jukeboxByDefault = false,
            allowSelfSignedCertificate = false,
            ldapSupport = false,
            musicFolderId = "",
            minimumApiVersion = null
        )
    }

    /**
     * Sets the Active Server by the Server Index in the Server Selector List
     * @param index: The index of the Active Server in the Server Selector List
     */
    fun setActiveServerByIndex(index: Int) {
        Timber.d("setActiveServerByIndex $index")
        if (index < 1) {
            // Offline mode is selected
            setActiveServerId(context, 0)
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            val serverId = repository.findByIndex(index)?.id ?: 0
            setActiveServerId(context, serverId)
        }
    }

    /**
     * Sets the minimum Subsonic API version of the current server.
     */
    fun setMinimumApiVersion(apiVersion: String) {
        GlobalScope.launch(Dispatchers.IO) {
            if (cachedServer != null) {
                cachedServer!!.minimumApiVersion = apiVersion
                repository.update(cachedServer!!)
            }
        }
    }

    /**
     * Invalidates the Active Server Setting cache
     * This should be called when the Active Server or one of its properties changes
     */
    fun invalidateCache() {
        Timber.d("Cache is invalidated")
        cachedServer = null
    }

    /**
     * Gets the Rest Url of the Active Server
     * @param method: The Rest resource to use
     * @return The Rest Url of the method on the server
     */
    fun getRestUrl(method: String?): String? {
        val builder = StringBuilder(8192)
        val activeServer = getActiveServer()
        val serverUrl: String = activeServer.url
        val username: String = activeServer.userName
        var password: String = activeServer.password

        // Slightly obfuscate password
        password = "enc:" + Util.utf8HexEncode(password)
        builder.append(serverUrl)
        if (builder[builder.length - 1] != '/') {
            builder.append('/')
        }
        builder.append("rest/").append(method).append(".view")
        builder.append("?u=").append(username)
        builder.append("&p=").append(password)
        builder.append("&v=").append(Constants.REST_PROTOCOL_VERSION)
        builder.append("&c=").append(Constants.REST_CLIENT_ID)
        return builder.toString()
    }

    companion object {
        /**
         * Queries if the Active Server is the "Offline" mode of Ultrasonic
         * @return True, if the "Offline" mode is selected
         */
        fun isOffline(context: Context?): Boolean {
            return context == null || getActiveServerId(context) < 1
        }

        /**
         * Queries the Id of the Active Server
         */
        fun getActiveServerId(context: Context): Int {
            val preferences = Util.getPreferences()
            return preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, -1)
        }

        /**
         * Sets the Id of the Active Server
         */
        fun setActiveServerId(context: Context, serverId: Int) {
            resetMusicService()

            val preferences = Util.getPreferences()
            val editor = preferences.edit()
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, serverId)
            editor.apply()
        }

        /**
         * Queries if Scrobbling is enabled
         */
        fun isScrobblingEnabled(context: Context): Boolean {
            if (isOffline(context)) {
                return false
            }
            val preferences = Util.getPreferences()
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SCROBBLE, false)
        }

        /**
         * Queries if Server Scaling is enabled
         */
        fun isServerScalingEnabled(context: Context): Boolean {
            if (isOffline(context)) {
                return false
            }
            val preferences = Util.getPreferences()
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SERVER_SCALING, false)
        }
    }
}
