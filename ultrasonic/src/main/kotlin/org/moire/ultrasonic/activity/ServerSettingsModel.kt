package org.moire.ultrasonic.activity

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.data.ServerSettingDao

/**
 * ViewModel to be used in Activities which will handle Server Settings
 */
class ServerSettingsModel(
    private val repository: ServerSettingDao,
    private val activeServerProvider: ActiveServerProvider,
    private val context: Context
) : ViewModel() {
    private var serverList: MutableLiveData<List<ServerSetting>> = MutableLiveData()

    companion object {
        private val TAG = ServerSettingsModel::class.simpleName
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
                val settings = PreferenceManager.getDefaultSharedPreferences(context)
                val serverNum = settings.getInt(PREFERENCES_KEY_ACTIVE_SERVERS, 0)

                if (serverNum != 0) {
                    for (x in 1 until serverNum + 1) {
                        val newServerSetting = loadServerSettingFromPreferences(x, settings)
                        if (newServerSetting != null) {
                            dbServerList.add(newServerSetting)
                            repository.insert(newServerSetting)
                            Log.i(
                                TAG,
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
        viewModelScope.launch {
            val dbServerList = repository.loadAllServerSettings().toMutableList()

            dbServerList.add(0, ServerSetting(context.getString(R.string.main_offline), ""))
            serverList.value = dbServerList
        }
        return serverList
    }

    /**
     * Retrieves a single Server Setting by its index
     * This function is asynchronous, uses LiveData to provide the Setting.
     */
    fun getServerSetting(index: Int): LiveData<ServerSetting?> {
        val result = MutableLiveData<ServerSetting?>()
        viewModelScope.launch {
            val dbServer = repository.findByIndex(index)
            result.value = dbServer
            Log.d(TAG, "getServerSetting($index) returning $dbServer")
        }
        return result
    }

    /**
     * Moves a Setting up in the Server List by decreasing its index
     */
    fun moveItemUp(index: Int) {
        if (index == 1) return

        val itemToBeMoved = serverList.value?.single { setting -> setting.index == index }
        val previousItem = serverList.value?.single { setting -> setting.index == index - 1 }

        itemToBeMoved?.index = previousItem!!.index
        previousItem.index = index

        viewModelScope.launch {
            repository.update(itemToBeMoved!!, previousItem)
        }

        activeServerProvider.invalidateCache()
        // Notify the observers of the changed values
        serverList.value = serverList.value
    }

    /**
     * Moves a Setting down in the Server List by increasing its index
     */
    fun moveItemDown(index: Int) {
        if (index == (serverList.value!!.size - 1)) return

        val itemToBeMoved = serverList.value?.single { setting -> setting.index == index }
        val nextItem = serverList.value?.single { setting -> setting.index == index + 1 }

        itemToBeMoved?.index = nextItem!!.index
        nextItem.index = index

        viewModelScope.launch {
            repository.update(itemToBeMoved!!, nextItem)
        }

        activeServerProvider.invalidateCache()
        // Notify the observers of the changed values
        serverList.value = serverList.value
    }

    /**
     * Removes a Setting from the database
     */
    fun deleteItem(index: Int) {
        if (index == 0) return

        val newList = serverList.value!!.toMutableList()
        val itemToBeDeleted = newList.single { setting -> setting.index == index }
        newList.remove(itemToBeDeleted)

        for (x in index + 1 until newList.size + 1) {
            newList.single { setting -> setting.index == x }.index--
        }

        viewModelScope.launch {
            repository.delete(itemToBeDeleted)
            for (x in index until newList.size) {
                repository.update(newList.single { setting -> setting.index == x })
            }
        }

        activeServerProvider.invalidateCache()
        serverList.value = newList
        Log.d(TAG, "deleteItem deleted index: $index")
    }

    /**
     * Updates a Setting in the database
     */
    fun updateItem(serverSetting: ServerSetting?) {
        if (serverSetting == null) return

        viewModelScope.launch {
            repository.update(serverSetting)
            activeServerProvider.invalidateCache()
            Log.d(TAG, "updateItem updated server setting: $serverSetting")
        }
    }

    /**
     * Inserts a new Setting into the database
     */
    fun saveNewItem(serverSetting: ServerSetting?) {
        if (serverSetting == null) return

        viewModelScope.launch {
            serverSetting.index = (repository.count() ?: 0) + 1
            serverSetting.id = serverSetting.index
            repository.insert(serverSetting)
            Log.d(TAG, "saveNewItem saved server setting: $serverSetting")
        }
    }

    /**
     * Reads up a Server Setting stored in the obsolete Preferences
     */
    private fun loadServerSettingFromPreferences(
        id: Int,
        settings: SharedPreferences
    ): ServerSetting? {
        val url = settings.getString(PREFERENCES_KEY_SERVER_URL + id, "")
        val userName = settings.getString(PREFERENCES_KEY_USERNAME + id, "")

        if (url.isNullOrEmpty() || userName.isNullOrEmpty()) return null

        return ServerSetting(
            id,
            id,
            settings.getString(PREFERENCES_KEY_SERVER_NAME + id, "")!!,
            url,
            userName,
            settings.getString(PREFERENCES_KEY_PASSWORD + id, "")!!,
            settings.getBoolean(PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + id, false),
            settings.getBoolean(PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + id, false),
            settings.getBoolean(PREFERENCES_KEY_LDAP_SUPPORT + id, false),
            settings.getString(PREFERENCES_KEY_MUSIC_FOLDER_ID + id, "")!!
        )
    }
}
