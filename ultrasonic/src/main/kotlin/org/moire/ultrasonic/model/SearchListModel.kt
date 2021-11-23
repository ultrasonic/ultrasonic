package org.moire.ultrasonic.model

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.fragment.SearchFragment
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Settings

class SearchListModel(application: Application) : GenericListModel(application) {

    var searchResult: MutableLiveData<SearchResult?> = MutableLiveData(null)

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh, args)
    }

    suspend fun search(query: String) {
        val maxArtists = Settings.maxArtists
        val maxAlbums = Settings.maxAlbums
        val maxSongs = Settings.maxSongs

        withContext(Dispatchers.IO) {
            val criteria = SearchCriteria(query, maxArtists, maxAlbums, maxSongs)
            val service = MusicServiceFactory.getMusicService()
            val result = service.search(criteria)

            if (result != null) searchResult.postValue(result)
        }
    }

    fun trimResultLength(result: SearchResult): SearchResult {
        return SearchResult(
            artists = result.artists.take(SearchFragment.DEFAULT_ARTISTS),
            albums = result.albums.take(SearchFragment.DEFAULT_ALBUMS),
            songs = result.songs.take(SearchFragment.DEFAULT_SONGS)
        )
    }

//    fun mergeList(result: SearchResult): List<Identifiable> {
//        val list = mutableListOf<Identifiable>()
//        list.add(result.artists)
//        list.add(result.albums)
//        list.add(result.songs)
//        return list
//    }
}
