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

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.service.MusicService

/**
 * Provides ViewModel which contains the list of available Artists
 */
@KoinApiExtension
class ArtistListModel(application: Application) : GenericListModel(application) {
    private val artists: MutableLiveData<List<Artist>> = MutableLiveData()

    /**
     * Retrieves all available Artists in a LiveData
     */
    fun getItems(refresh: Boolean, swipe: SwipeRefreshLayout): LiveData<List<Artist>> {
        backgroundLoadFromServer(refresh, swipe)
        return artists
    }

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh, args)

        val musicFolderId = activeServer.musicFolderId

        val result = if (!isOffline && useId3Tags)
            musicService.getArtists(refresh)
        else musicService.getIndexes(musicFolderId, refresh)

        val retrievedArtists: MutableList<Artist> =
            ArrayList(result.shortcuts.size + result.artists.size)
        retrievedArtists.addAll(result.shortcuts)
        retrievedArtists.addAll(result.artists)
        artists.postValue(retrievedArtists)
    }
}
