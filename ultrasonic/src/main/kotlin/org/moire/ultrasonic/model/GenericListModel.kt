package org.moire.ultrasonic.model

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Settings

/**
* An abstract Model, which can be extended to retrieve a list of items from the API
*/
open class GenericListModel(application: Application) :
    AndroidViewModel(application), KoinComponent {

    val activeServerProvider: ActiveServerProvider by inject()

    val activeServer: ServerSetting
        get() = activeServerProvider.getActiveServer()

    val context: Context
        get() = getApplication<Application>().applicationContext

    var currentListIsSortable = true
    var showHeader = true

    @Suppress("UNUSED_PARAMETER")
    open fun showSelectFolderHeader(args: Bundle?): Boolean {
        return true
    }

    /**
     * Helper function to check online status
     */
    fun isOffline(): Boolean {
        return ActiveServerProvider.isOffline()
    }

    /**
     * Refreshes the cached items from the server
     */
    fun refresh(swipe: SwipeRefreshLayout, bundle: Bundle?) {
        backgroundLoadFromServer(true, swipe, bundle ?: Bundle())
    }

    /**
     * Trigger a load() and notify the UI that we are loading
     */
    fun backgroundLoadFromServer(
        refresh: Boolean,
        swipe: SwipeRefreshLayout,
        bundle: Bundle = Bundle()
    ) {
        viewModelScope.launch {
            swipe.isRefreshing = true
            loadFromServer(refresh, swipe, bundle)
            swipe.isRefreshing = false
        }
    }

    /**
     * Calls the load() function with error handling
     */
    suspend fun loadFromServer(refresh: Boolean, swipe: SwipeRefreshLayout, bundle: Bundle) =
        withContext(Dispatchers.IO) {
            val musicService = MusicServiceFactory.getMusicService()
            val isOffline = ActiveServerProvider.isOffline()
            val useId3Tags = Settings.shouldUseId3Tags

            try {
                load(isOffline, useId3Tags, musicService, refresh, bundle)
            } catch (all: Exception) {
                handleException(all, swipe.context)
            }
        }

    private fun handleException(exception: Exception, context: Context) {
        Handler(Looper.getMainLooper()).post {
            CommunicationError.handleError(exception, context)
        }
    }

    /**
     * This is the central function you need to implement if you want to extend this class
     */
    open fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        // Update the list of available folders if enabled
        if (showSelectFolderHeader(args) && !isOffline && !useId3Tags) {
            // FIXME
        }
    }

    /**
     * Some shared helper functions
     */

    // Returns true if the directory contains only folders
    internal fun hasOnlyFolders(musicDirectory: MusicDirectory) =
        musicDirectory.getChildren(includeDirs = true, includeFiles = false).size ==
            musicDirectory.getChildren(includeDirs = true, includeFiles = true).size

    internal val allSongsId = "-1"
}
