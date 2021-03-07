/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2020 (C) Jozsef Varga
 */
package org.moire.ultrasonic.fragment

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.CommunicationErrorHandler
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/**
 * Provides ViewModel which contains the list of available Artists
 */
class ArtistListModel(
    private val activeServerProvider: ActiveServerProvider,
    private val context: Context
) : ViewModel() {
    private val musicFolders: MutableLiveData<List<MusicFolder>> = MutableLiveData()
    private val artists: MutableLiveData<List<Artist>> = MutableLiveData()

    /**
     * Retrieves the available Artists in a LiveData
     */
    fun getArtists(refresh: Boolean, swipe: SwipeRefreshLayout): LiveData<List<Artist>> {
        backgroundLoadFromServer(refresh, swipe)
        return artists
    }

    /**
     * Retrieves the available Music Folders in a LiveData
     */
    fun getMusicFolders(): LiveData<List<MusicFolder>> {
        return musicFolders
    }

    /**
     * Refreshes the cached Artists from the server
     */
    fun refresh(swipe: SwipeRefreshLayout) {
        backgroundLoadFromServer(true, swipe)
    }

    private fun backgroundLoadFromServer(refresh: Boolean, swipe: SwipeRefreshLayout) {
        viewModelScope.launch {
            swipe.isRefreshing = true
            loadFromServer(refresh, swipe)
            swipe.isRefreshing = false
        }
    }

    private suspend fun loadFromServer(refresh: Boolean, swipe: SwipeRefreshLayout) =
        withContext(Dispatchers.IO) {
            val musicService = MusicServiceFactory.getMusicService(context)
            val isOffline = ActiveServerProvider.isOffline(context)
            val useId3Tags = Util.getShouldUseId3Tags(context)

            try {
                if (!isOffline && !useId3Tags) {
                    musicFolders.postValue(
                        musicService.getMusicFolders(refresh, context)
                    )
                }

                val musicFolderId = activeServerProvider.getActiveServer().musicFolderId

                val result = if (!isOffline && useId3Tags)
                    musicService.getArtists(refresh, context)
                else musicService.getIndexes(musicFolderId, refresh, context)

                val retrievedArtists: MutableList<Artist> =
                    ArrayList(result.shortcuts.size + result.artists.size)
                retrievedArtists.addAll(result.shortcuts)
                retrievedArtists.addAll(result.artists)
                artists.postValue(retrievedArtists)
            } catch (exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    CommunicationErrorHandler.handleError(exception, swipe.context)
                }
            }
        }
}
