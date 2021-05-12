package org.moire.ultrasonic.fragment

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.data.ServerSettingDao
import timber.log.Timber

/**
 * ViewModel to be used in Activities which will handle Server Settings
 */
class ServerSettingsModel(
    private val repository: ServerSettingDao,
    private val activeServerProvider: ActiveServerProvider,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val PREFERENCES_KEY_SERVER_MIGRATED = "serverMigrated"
        // These constants were removed from Constants.java as they are deprecated and only used here
        private const val PREFERENCES_KEY_JUKEBOX_BY_DEFAULT = "jukeboxEnabled"
        private const val PREFERENCES_KEY_SERVER_NAME = "serverName"
        private const val PREFERENCES_KEY_SERVER_URL = "serverUrl"
        private const val PREFERENCES_KEY_ACTIVE_SERVERS = "activeServers"
        private const val PREFERENCES_KEY_USERNAME = "username"
        private const val PREFERENCES_KEY_PASSWORD = "password"
        private const val PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE = "allowSSCertificate"
        private const val PREFERENCES_KEY_LDAP_SUPPORT = "enableLdapSupport"
        private const val PREFERENCES_KEY_MUSIC_FOLDER_ID = "musicFolderId"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * This function will try and convert settings from the Preferences to the Database
     * @return True, if the migration was executed, False otherwise
     */
    fun migrateFromPreferences(): Boolean {
        var migrated = true

        runBlocking {
            val rowCount = repository.count()

            if (rowCount == null || rowCount == 0) {
                // First time load up the server settings from the Preferences
                val dbServerList = mutableListOf<ServerSetting>()
                val context = getApplication<Application>().applicationContext
                val settings = PreferenceManager.getDefaultSharedPreferences(context)
                val serverNum = settings.getInt(PREFERENCES_KEY_ACTIVE_SERVERS, 0)

                if (serverNum != 0) {
                    var index = 1
                    for (x in 1 until serverNum + 1) {
                        val newServerSetting = loadServerSettingFromPreferences(x, index, settings)
                        if (newServerSetting != null) {
                            dbServerList.add(newServerSetting)
                            repository.insert(newServerSetting)
                            index++
                            Timber.i(
                                "Imported server from Preferences to Database:" +
                                    " ${newServerSetting.name}"
                            )
                        }
                    }
                } else {
                    migrated = false
                }
            }
        }

        return migrated
    }

    /**
     * Retrieves the list of the configured servers from the database.
     * This function is asynchronous, uses LiveData to provide the Setting.
     */
    fun getServerList(): LiveData<List<ServerSetting>> {
        // This check should run before returning any result
        runBlocking {
            if (areIndexesMissing()) {
                reindexSettings()
            }
        }
        return repository.loadAllServerSettings()
    }

    /**
     * Retrieves a single Server Setting by its index
     * This function is asynchronous, uses LiveData to provide the Setting.
     */
    fun getServerSetting(index: Int): LiveData<ServerSetting?> {
        return repository.getLiveServerSettingByIndex(index)
    }

    /**
     * Moves a Setting up in the Server List by decreasing its index
     */
    fun moveItemUp(index: Int) {
        if (index <= 1) return

        viewModelScope.launch {
            val itemToBeMoved = repository.findByIndex(index)
            val previousItem = repository.findByIndex(index - 1)

            if (itemToBeMoved != null && previousItem != null) {
                itemToBeMoved.index = previousItem.index
                previousItem.index = index

                repository.update(itemToBeMoved, previousItem)
                activeServerProvider.invalidateCache()
            }
        }
    }

    /**
     * Moves a Setting down in the Server List by increasing its index
     */
    fun moveItemDown(index: Int) {
        viewModelScope.launch {
            if (index < repository.getMaxIndex() ?: 0) {
                val itemToBeMoved = repository.findByIndex(index)
                val nextItem = repository.findByIndex(index + 1)

                if (itemToBeMoved != null && nextItem != null) {
                    itemToBeMoved.index = nextItem.index
                    nextItem.index = index

                    repository.update(itemToBeMoved, nextItem)
                    activeServerProvider.invalidateCache()
                }
            }
        }
    }

    /**
     * Removes a Setting from the database
     */
    fun deleteItem(index: Int) {
        if (index == 0) return

        viewModelScope.launch {
            val itemToBeDeleted = repository.findByIndex(index)
            if (itemToBeDeleted != null) {
                repository.delete(itemToBeDeleted)
                Timber.d("deleteItem deleted index: $index")
                reindexSettings()
                activeServerProvider.invalidateCache()
            }
        }
    }

    /**
     * Updates a Setting in the database
     */
    fun updateItem(serverSetting: ServerSetting?) {
        if (serverSetting == null) return

        appScope.launch {
            repository.update(serverSetting)
            activeServerProvider.invalidateCache()
            Timber.d("updateItem updated server setting: $serverSetting")
        }
    }

    /**
     * Inserts a new Setting into the database
     */
    fun saveNewItem(serverSetting: ServerSetting?) {
        if (serverSetting == null) return

        appScope.launch {
            serverSetting.index = (repository.count() ?: 0) + 1
            serverSetting.id = (repository.getMaxId() ?: 0) + 1
            repository.insert(serverSetting)
            Timber.d("saveNewItem saved server setting: $serverSetting")
        }
    }

    /**
     * Reads up a Server Setting stored in the obsolete Preferences
     */
    private fun loadServerSettingFromPreferences(
        preferenceId: Int,
        serverId: Int,
        settings: SharedPreferences
    ): ServerSetting? {
        val url = settings.getString(PREFERENCES_KEY_SERVER_URL + preferenceId, "")
        val userName = settings.getString(PREFERENCES_KEY_USERNAME + preferenceId, "")
        val isMigrated = settings.getBoolean(PREFERENCES_KEY_SERVER_MIGRATED + preferenceId, false)

        if (url.isNullOrEmpty() || userName.isNullOrEmpty() || isMigrated) return null
        setServerMigrated(settings, preferenceId)

        return ServerSetting(
            preferenceId,
            serverId,
            settings.getString(PREFERENCES_KEY_SERVER_NAME + preferenceId, "")!!,
            url,
            userName,
            settings.getString(PREFERENCES_KEY_PASSWORD + preferenceId, "")!!,
            settings.getBoolean(PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + preferenceId, false),
            settings.getBoolean(
                PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + preferenceId,
                false
            ),
            settings.getBoolean(PREFERENCES_KEY_LDAP_SUPPORT + preferenceId, false),
            settings.getString(PREFERENCES_KEY_MUSIC_FOLDER_ID + preferenceId, null),
            null
        )
    }

    /**
     * Checks if there are any missing indexes in the ServerSetting list
     * For displaying the Server Settings in a ListView, it is mandatory that their indexes
     * aren't missing. Ideally the indexes are continuous, but some circumstances (e.g.
     * concurrency or migration errors) may get them out of order.
     * This would make the List Adapter crash, so it is best to prepare and check the list.
     */
    private suspend fun areIndexesMissing(): Boolean {
        for (i in 1 until getMaximumIndexToCheck() + 1) {
            if (repository.findByIndex(i) == null) return true
        }
        return false
    }

    /**
     * This function updates all the Server Settings in the DB so their indexing is continuous.
     */
    private suspend fun reindexSettings() {
        var newIndex = 1
        for (i in 1 until getMaximumIndexToCheck() + 1) {
            val setting = repository.findByIndex(i)
            if (setting != null) {
                setting.index = newIndex
                newIndex++
                repository.update(setting)
                Timber.d("reindexSettings saved $setting")
            }
        }
    }

    private suspend fun getMaximumIndexToCheck(): Int {
        val rowsInDatabase = repository.count() ?: 0
        val indexesInDatabase = repository.getMaxIndex() ?: 0
        if (rowsInDatabase > indexesInDatabase) return rowsInDatabase
        return indexesInDatabase
    }

    private fun setServerMigrated(settings: SharedPreferences, preferenceId: Int) {
        val editor = settings.edit()
        editor.putBoolean(PREFERENCES_KEY_SERVER_MIGRATED + preferenceId, true)
        editor.apply()
    }
}
