/*
 * ArtistListModel.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.model

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.Collator
import org.moire.ultrasonic.data.ActiveServerProvider
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

        val result = if (ActiveServerProvider.isID3Enabled()) {
            musicService.getArtists(refresh)
        } else {
            musicService.getIndexes(musicFolderId, refresh)
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
