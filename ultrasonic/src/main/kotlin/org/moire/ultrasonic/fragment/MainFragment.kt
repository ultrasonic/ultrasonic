package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.MergeAdapter
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Displays the Main screen of Ultrasonic, where the music library can be browsed
 */
class MainFragment : Fragment(), KoinComponent {
    private var list: ListView? = null

    private lateinit var musicTitle: View
    private lateinit var artistsButton: View
    private lateinit var albumsButton: View
    private lateinit var genresButton: View
    private lateinit var videosTitle: View
    private lateinit var songsTitle: View
    private lateinit var randomSongsButton: View
    private lateinit var songsStarredButton: View
    private lateinit var albumsTitle: View
    private lateinit var albumsNewestButton: View
    private lateinit var albumsRandomButton: View
    private lateinit var albumsHighestButton: View
    private lateinit var albumsStarredButton: View
    private lateinit var albumsRecentButton: View
    private lateinit var albumsFrequentButton: View
    private lateinit var albumsAlphaByNameButton: View
    private lateinit var albumsAlphaByArtistButton: View
    private lateinit var videosButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.main_list)

        setupButtons()

        if (list != null) setupMenuList(list!!)

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        var shouldRestart = false
        val currentId3Setting = Settings.shouldUseId3Tags

        // If setting has changed...
        if (currentId3Setting != shouldUseId3) {
            shouldUseId3 = currentId3Setting
            shouldRestart = true
        }

        // then setup the list anew.
        if (shouldRestart) {
            if (list != null) setupMenuList(list!!)
        }
    }

    private fun setupButtons() {
        val buttons = layoutInflater.inflate(R.layout.main_buttons, list, false)
        musicTitle = buttons.findViewById(R.id.main_music)
        artistsButton = buttons.findViewById(R.id.main_artists_button)
        albumsButton = buttons.findViewById(R.id.main_albums_button)
        genresButton = buttons.findViewById(R.id.main_genres_button)
        videosTitle = buttons.findViewById(R.id.main_videos_title)
        songsTitle = buttons.findViewById(R.id.main_songs)
        randomSongsButton = buttons.findViewById(R.id.main_songs_button)
        songsStarredButton = buttons.findViewById(R.id.main_songs_starred)
        albumsTitle = buttons.findViewById(R.id.main_albums)
        albumsNewestButton = buttons.findViewById(R.id.main_albums_newest)
        albumsRandomButton = buttons.findViewById(R.id.main_albums_random)
        albumsHighestButton = buttons.findViewById(R.id.main_albums_highest)
        albumsStarredButton = buttons.findViewById(R.id.main_albums_starred)
        albumsRecentButton = buttons.findViewById(R.id.main_albums_recent)
        albumsFrequentButton = buttons.findViewById(R.id.main_albums_frequent)
        albumsAlphaByNameButton = buttons.findViewById(R.id.main_albums_alphaByName)
        albumsAlphaByArtistButton = buttons.findViewById(R.id.main_albums_alphaByArtist)
        videosButton = buttons.findViewById(R.id.main_videos)
    }

    private fun setupMenuList(list: ListView) {

        // TODO: Should use RecyclerView
        val adapter = MergeAdapter()

        shouldUseId3 = Settings.shouldUseId3Tags

        if (!isOffline()) {
            adapter.addView(musicTitle, false)
            adapter.addViews(listOf(artistsButton, albumsButton, genresButton), true)
            adapter.addView(songsTitle, false)
            adapter.addViews(listOf(randomSongsButton, songsStarredButton), true)
            adapter.addView(albumsTitle, false)
            adapter.addViews(
                listOf(
                    albumsNewestButton,
                    albumsRecentButton,
                    albumsFrequentButton
                ),
                true
            )
            if (!shouldUseId3) {
                adapter.addView(albumsHighestButton, true)
            }
            adapter.addViews(
                listOf(
                    albumsRandomButton,
                    albumsStarredButton,
                    albumsAlphaByNameButton,
                    albumsAlphaByArtistButton
                ),
                true
            )
            adapter.addView(videosTitle, false)
            adapter.addViews(listOf(videosButton), true)
        } else {
            // Offline supported calls
            adapter.addView(musicTitle, false)
            adapter.addViews(listOf(artistsButton, genresButton), true)
            adapter.addView(songsTitle, false)
            adapter.addView(randomSongsButton, true)
        }

        list.adapter = adapter
        list.onItemClickListener = listListener
    }

    private val listListener =
        OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            when {
                view === albumsNewestButton -> {
                    showAlbumList("newest", R.string.main_albums_newest)
                }
                view === albumsRandomButton -> {
                    showAlbumList("random", R.string.main_albums_random)
                }
                view === albumsHighestButton -> {
                    showAlbumList("highest", R.string.main_albums_highest)
                }
                view === albumsRecentButton -> {
                    showAlbumList("recent", R.string.main_albums_recent)
                }
                view === albumsFrequentButton -> {
                    showAlbumList("frequent", R.string.main_albums_frequent)
                }
                view === albumsStarredButton -> {
                    showAlbumList(Constants.STARRED, R.string.main_albums_starred)
                }
                view === albumsAlphaByNameButton -> {
                    showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_alphaByName)
                }
                view === albumsAlphaByArtistButton -> {
                    showAlbumList("alphabeticalByArtist", R.string.main_albums_alphaByArtist)
                }
                view === songsStarredButton -> {
                    showStarredSongs()
                }
                view === artistsButton -> {
                    showArtists()
                }
                view === albumsButton -> {
                    showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_title)
                }
                view === randomSongsButton -> {
                    showRandomSongs()
                }
                view === genresButton -> {
                    showGenres()
                }
                view === videosButton -> {
                    showVideos()
                }
            }
        }

    private fun showStarredSongs() {
        val bundle = Bundle()
        bundle.putInt(Constants.INTENT_STARRED, 1)
        Navigation.findNavController(requireView()).navigate(R.id.mainToTrackCollection, bundle)
    }

    private fun showRandomSongs() {
        val bundle = Bundle()
        bundle.putInt(Constants.INTENT_RANDOM, 1)
        bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.maxSongs)
        Navigation.findNavController(requireView()).navigate(R.id.mainToTrackCollection, bundle)
    }

    private fun showArtists() {
        val bundle = Bundle()
        bundle.putString(
            Constants.INTENT_ALBUM_LIST_TITLE,
            requireContext().resources.getString(R.string.main_artists_title)
        )
        Navigation.findNavController(requireView()).navigate(R.id.mainToArtistList, bundle)
    }

    private fun showAlbumList(type: String, titleIndex: Int) {
        val bundle = Bundle()
        val title = requireContext().resources.getString(titleIndex, "")
        bundle.putString(Constants.INTENT_ALBUM_LIST_TYPE, type)
        bundle.putString(Constants.INTENT_ALBUM_LIST_TITLE, title)
        bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, Settings.maxAlbums)
        bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0)
        Navigation.findNavController(requireView()).navigate(R.id.mainToAlbumList, bundle)
    }

    private fun showGenres() {
        Navigation.findNavController(requireView()).navigate(R.id.mainToSelectGenre)
    }

    private fun showVideos() {
        val bundle = Bundle()
        bundle.putInt(Constants.INTENT_VIDEOS, 1)
        Navigation.findNavController(requireView()).navigate(R.id.mainToTrackCollection, bundle)
    }

    companion object {
        private var shouldUseId3 = false
    }
}
