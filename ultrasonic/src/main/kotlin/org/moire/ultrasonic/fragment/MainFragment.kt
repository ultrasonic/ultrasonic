package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.databinding.MainBinding
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Displays the Main screen of Ultrasonic, where the music library can be browsed
 */
class MainFragment : Fragment(), KoinComponent {

    private lateinit var musicTitle: TextView
    private lateinit var artistsButton: TextView
    private lateinit var albumsButton: TextView
    private lateinit var genresButton: TextView
    private lateinit var videosTitle: TextView
    private lateinit var songsTitle: TextView
    private lateinit var randomSongsButton: TextView
    private lateinit var songsStarredButton: TextView
    private lateinit var albumsTitle: TextView
    private lateinit var albumsNewestButton: TextView
    private lateinit var albumsRandomButton: TextView
    private lateinit var albumsHighestButton: TextView
    private lateinit var albumsStarredButton: TextView
    private lateinit var albumsRecentButton: TextView
    private lateinit var albumsFrequentButton: TextView
    private lateinit var albumsAlphaByNameButton: TextView
    private lateinit var albumsAlphaByArtistButton: TextView
    private lateinit var videosButton: TextView

    private var binding: MainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupButtons()
        setupClickListener()
        setupItemVisibility()

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        var shouldRestart = false
        val currentId3Setting = Settings.shouldUseId3Tags

        // If setting has changed...
        if (currentId3Setting != cachedId3Setting) {
            cachedId3Setting = currentId3Setting
            shouldRestart = true
        }

        // then setup the list anew.
        if (shouldRestart) {
            setupItemVisibility()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setupButtons() {
        musicTitle = binding!!.mainMusic
        artistsButton = binding!!.mainArtistsButton
        albumsButton = binding!!.mainAlbumsButton
        genresButton = binding!!.mainGenresButton
        videosTitle = binding!!.mainVideosTitle
        songsTitle = binding!!.mainSongs
        randomSongsButton = binding!!.mainSongsButton
        songsStarredButton = binding!!.mainSongsStarred
        albumsTitle = binding!!.mainAlbums
        albumsNewestButton = binding!!.mainAlbumsNewest
        albumsRandomButton = binding!!.mainAlbumsRandom
        albumsHighestButton = binding!!.mainAlbumsHighest
        albumsStarredButton = binding!!.mainAlbumsStarred
        albumsRecentButton = binding!!.mainAlbumsRecent
        albumsFrequentButton = binding!!.mainAlbumsFrequent
        albumsAlphaByNameButton = binding!!.mainAlbumsAlphaByName
        albumsAlphaByArtistButton = binding!!.mainAlbumsAlphaByArtist
        videosButton = binding!!.mainVideos
    }

    private fun setupItemVisibility() {
        // Cache some values
        cachedId3Setting = Settings.shouldUseId3Tags
        val isOnline = !isOffline()

        // Music
        musicTitle.isVisible = true
        artistsButton.isVisible = true
        albumsButton.isVisible = isOnline
        genresButton.isVisible = true

        // Songs
        songsTitle.isVisible = isOnline
        randomSongsButton.isVisible = true
        songsStarredButton.isVisible = isOnline

        // Albums
        albumsTitle.isVisible = isOnline
        albumsNewestButton.isVisible = isOnline
        albumsRecentButton.isVisible = isOnline
        albumsFrequentButton.isVisible = isOnline
        albumsHighestButton.isVisible = isOnline && !cachedId3Setting
        albumsRandomButton.isVisible = isOnline
        albumsStarredButton.isVisible = isOnline
        albumsAlphaByNameButton.isVisible = isOnline
        albumsAlphaByArtistButton.isVisible = isOnline

        // Videos
        videosTitle.isVisible = isOnline
        videosButton.isVisible = isOnline
    }

    private fun setupClickListener() {
        albumsNewestButton.setOnClickListener {
            showAlbumList("newest", R.string.main_albums_newest)
        }

        albumsRandomButton.setOnClickListener {
            showAlbumList("random", R.string.main_albums_random)
        }

        albumsHighestButton.setOnClickListener {
            showAlbumList("highest", R.string.main_albums_highest)
        }

        albumsRecentButton.setOnClickListener {
            showAlbumList("recent", R.string.main_albums_recent)
        }

        albumsFrequentButton.setOnClickListener {
            showAlbumList("frequent", R.string.main_albums_frequent)
        }

        albumsStarredButton.setOnClickListener {
            showAlbumList(Constants.STARRED, R.string.main_albums_starred)
        }

        albumsAlphaByNameButton.setOnClickListener {
            showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_alphaByName)
        }

        albumsAlphaByArtistButton.setOnClickListener {
            showAlbumList("alphabeticalByArtist", R.string.main_albums_alphaByArtist)
        }

        songsStarredButton.setOnClickListener {
            showStarredSongs()
        }

        artistsButton.setOnClickListener {
            showArtists()
        }

        albumsButton.setOnClickListener {
            showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_title)
        }

        randomSongsButton.setOnClickListener {
            showRandomSongs()
        }

        genresButton.setOnClickListener {
            showGenres()
        }

        videosButton.setOnClickListener {
            showVideos()
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
        private var cachedId3Setting = false
    }
}
