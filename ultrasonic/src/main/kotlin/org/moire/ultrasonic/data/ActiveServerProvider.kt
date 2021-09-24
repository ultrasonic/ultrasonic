package org.moire.ultrasonic.data

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.di.DB_FILENAME
import org.moire.ultrasonic.service.MusicServiceFactory.resetMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * This class can be used to retrieve the properties of the Active Server
 * It caches the settings read up from the DB to improve performance.
 */
class ActiveServerProvider(
    private val repository: ServerSettingDao
) {
    private var cachedServer: ServerSetting? = null
    private var cachedDatabase: MetaDatabase? = null
    private var cachedServerId: Int? = null

    /**
     * Get the settings of the current Active Server
     * @return The Active Server Settings
     */
    fun getActiveServer(): ServerSetting {
        val serverId = getActiveServerId()

        if (serverId > 0) {
            if (cachedServer != null && cachedServer!!.id == serverId) return cachedServer!!

            // Ideally this is the only call where we block the thread while using the repository
            runBlocking {
                withContext(Dispatchers.IO) {
                    cachedServer = repository.findById(serverId)
                }
                Timber.d(
                    "getActiveServer retrieved from DataBase, id: %s cachedServer: %s",
                    serverId, cachedServer
                )
            }

            if (cachedServer != null) {
                return cachedServer!!
            }

            setActiveServerId(0)
        }

        return ServerSetting(
            id = -1,
            index = 0,
            name = UApp.applicationContext().getString(R.string.main_offline),
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
            setActiveServerId(0)
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            val serverId = repository.findByIndex(index)?.id ?: 0
            setActiveServerId(serverId)
        }
    }

    @Synchronized
    fun getActiveMetaDatabase(): MetaDatabase {
        val activeServer = getActiveServerId()

        if (activeServer == cachedServerId && cachedDatabase != null) {
            return cachedDatabase!!
        }

        Timber.i("Switching to new database, id:$activeServer")
        cachedServerId = activeServer
        val db = Room.databaseBuilder(
            UApp.applicationContext(),
            MetaDatabase::class.java,
            METADATA_DB + cachedServerId
        )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        return db
    }

    @Synchronized
    fun deleteMetaDatabase(id: Int) {
        cachedDatabase?.close()
        UApp.applicationContext().deleteDatabase(METADATA_DB + id)
        Timber.i("Deleted metadataBase, id:$id")
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
    fun getRestUrl(method: String?): String {
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

        const val METADATA_DB = "$DB_FILENAME-meta-"

        /**
         * Queries if the Active Server is the "Offline" mode of Ultrasonic
         * @return True, if the "Offline" mode is selected
         */
        fun isOffline(): Boolean {
            return getActiveServerId() < 1
        }

        /**
         * Queries the Id of the Active Server
         */
        fun getActiveServerId(): Int {
            val preferences = Settings.preferences
            return preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, -1)
        }

        /**
         * Sets the Id of the Active Server
         */
        fun setActiveServerId(serverId: Int) {
            resetMusicService()

            val preferences = Settings.preferences
            val editor = preferences.edit()
            editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, serverId)
            editor.apply()
        }

        /**
         * Queries if Scrobbling is enabled
         */
        fun isScrobblingEnabled(): Boolean {
            if (isOffline()) {
                return false
            }
            val preferences = Settings.preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SCROBBLE, false)
        }

        /**
         * Queries if Server Scaling is enabled
         */
        fun isServerScalingEnabled(): Boolean {
            if (isOffline()) {
                return false
            }
            val preferences = Settings.preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SERVER_SCALING, false)
        }
    }
}
