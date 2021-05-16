package org.moire.ultrasonic.fragment

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.ConnectException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.CommunicationErrorHandler
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/**
* An abstract Model, which can be extended to retrieve a list of items from the API
*/
@KoinApiExtension
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

    internal val musicFolders: MutableLiveData<List<MusicFolder>> = MutableLiveData()

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
            val useId3Tags = Util.getShouldUseId3Tags()

            try {
                load(isOffline, useId3Tags, musicService, refresh, bundle)
            } catch (exception: ConnectException) {
                Handler(Looper.getMainLooper()).post {
                    CommunicationErrorHandler.handleError(exception, swipe.context)
                }
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
            musicFolders.postValue(
                musicService.getMusicFolders(refresh)
            )
        }
    }

    /**
     * Retrieves the available Music Folders in a LiveData
     */
    fun getMusicFolders(): LiveData<List<MusicFolder>> {
        return musicFolders
    }
}
