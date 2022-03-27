package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            repository.insert(serverSetting)
            Timber.d("saveNewItem saved server setting: $serverSetting")
        }
    }

    /**
     * Inserts a new Setting into the database
     * @return The id of the demo server
     */
    fun addDemoServer(): Int {
        val demo = DEMO_SERVER_CONFIG.copy()

        runBlocking {
            demo.index = (repository.count() ?: 0) + 1
            repository.insert(demo)
            Timber.d("Added demo server")
        }

        return demo.index
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

    companion object {
        private val DEMO_SERVER_CONFIG = ServerSetting(
            id = 0,
            index = 0,
            name = UApp.applicationContext().getString(R.string.server_menu_demo),
            url = "https://demo.ampache.dev",
            userName = "ultrasonic_demo",
            password = "W7DumQ3ZUR89Se3",
            jukeboxByDefault = false,
            allowSelfSignedCertificate = false,
            ldapSupport = false,
            musicFolderId = null,
            minimumApiVersion = "1.13.0",
            chatSupport = true,
            bookmarkSupport = true,
            shareSupport = true,
            podcastSupport = true
        )
    }
}
