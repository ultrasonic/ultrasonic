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
package org.moire.ultrasonic.model

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.Collator
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.service.MusicService

/**
 * Provides ViewModel which contains the list of available Artists
 */
class ArtistListModel(application: Application) : GenericListModel(application) {
    private val artists: MutableLiveData<List<ArtistOrIndex>> = MutableLiveData()

    /**
     * Retrieves all available Artists in a LiveData
     */
    fun getItems(refresh: Boolean, swipe: SwipeRefreshLayout): LiveData<List<ArtistOrIndex>> {
        // Don't reload the data if navigating back to the view that was active before.
        // This way, we keep the scroll position
        if (artists.value?.isEmpty() != false || refresh) {
            backgroundLoadFromServer(refresh, swipe)
        }
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

        val result: List<ArtistOrIndex>

        if (!isOffline && useId3Tags) {
            result = musicService.getArtists(refresh)
        } else {
            result = musicService.getIndexes(musicFolderId, refresh)
        }

        artists.postValue(result.toMutableList().sortedWith(comparator))
    }

    override fun showSelectFolderHeader(args: Bundle?): Boolean {
        return true
    }

    companion object {
        val comparator: Comparator<ArtistOrIndex> =
            compareBy(Collator.getInstance()) { t -> t.name }
    }
}
