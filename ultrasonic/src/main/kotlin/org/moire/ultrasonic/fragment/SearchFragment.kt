package org.moire.ultrasonic.fragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowBinder
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.DividerBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder.MoreButton
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.SearchListModel
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer.Companion.playVideo
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

/**
 * Initiates a search on the media library and displays the results
 */
class SearchFragment : MultiListFragment<Identifiable>(), KoinComponent {
    private var searchResult: SearchResult? = null
    private var searchRefresh: SwipeRefreshLayout? = null
    private var searchView: SearchView? = null

    private val mediaPlayerController: MediaPlayerController by inject()

    private val shareHandler: ShareHandler by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()

    private var cancellationToken: CancellationToken? = null

    override val listModel: SearchListModel by viewModels()

    override val mainLayout: Int = R.layout.search

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()
        setTitle(this, R.string.search_title)
        setHasOptionsMenu(true)

        listModel.searchResult.observe(
            viewLifecycleOwner
        ) {
            if (it != null) {
                // Shorten the display initially
                searchResult = it
                populateList(listModel.trimResultLength(it))
            }
        }

        searchRefresh = view.findViewById(R.id.swipe_refresh_view)
        searchRefresh!!.isEnabled = false

        registerForContextMenu(listView!!)

        // Register our data binders
        // IMPORTANT:
        // They need to be added in the order of most specific -> least specific.
        viewAdapter.register(
            ArtistRowBinder(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected,
                imageLoader = imageLoaderProvider.getImageLoader(),
                enableSections = false
            )
        )

        viewAdapter.register(
            AlbumRowBinder(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected,
                imageLoader = imageLoaderProvider.getImageLoader(),
                context = requireContext()
            )
        )

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected,
                checkable = false,
                draggable = false,
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner
            )
        )

        viewAdapter.register(
            DividerBinder()
        )

        viewAdapter.register(
            MoreButtonBinder()
        )

        // Fragment was started with a query (e.g. from voice search), try to execute search right away
        val arguments = arguments
        if (arguments != null) {
            val query = arguments.getString(Constants.INTENT_QUERY)
            val autoPlay = arguments.getBoolean(Constants.INTENT_AUTOPLAY, false)
            if (query != null) {
                return search(query, autoPlay)
            }
        }
    }

    /**
     * This method create the search bar above the recycler view
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val activity = activity ?: return
        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        inflater.inflate(R.menu.search, menu)
        val searchItem = menu.findItem(R.id.search_item)
        searchView = searchItem.actionView as SearchView
        val searchableInfo = searchManager.getSearchableInfo(requireActivity().componentName)
        searchView!!.setSearchableInfo(searchableInfo)

        val arguments = arguments
        val autoPlay = arguments != null &&
            arguments.getBoolean(Constants.INTENT_AUTOPLAY, false)
        val query = arguments?.getString(Constants.INTENT_QUERY)

        // If started with a query, enter it to the searchView
        if (query != null) {
            searchView!!.setQuery(query, false)
            searchView!!.clearFocus()
        }

        searchView!!.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return true
            }

            override fun onSuggestionClick(position: Int): Boolean {
                Timber.d("onSuggestionClick: %d", position)
                val cursor = searchView!!.suggestionsAdapter.cursor
                cursor.moveToPosition(position)

                // 2 is the index of col containing suggestion name.
                val suggestion = cursor.getString(2)
                searchView!!.setQuery(suggestion, true)
                return true
            }
        })

        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                Timber.d("onQueryTextSubmit: %s", query)
                searchView!!.clearFocus()
                search(query, autoPlay)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })

        searchView!!.setIconifiedByDefault(false)
        searchItem.expandActionView()
    }

    override fun onDestroyView() {
        Util.hideKeyboard(activity)
        cancellationToken?.cancel()
        super.onDestroyView()
    }

    private fun downloadBackground(save: Boolean, songs: List<Track?>) {
        val onValid = Runnable {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.downloadBackground(songs, save)
        }
        onValid.run()
    }

    private fun search(query: String, autoplay: Boolean) {
        listModel.viewModelScope.launch(CommunicationError.getHandler(context)) {
            refreshListView?.isRefreshing = true
            listModel.search(query)
            refreshListView?.isRefreshing = false
        }.invokeOnCompletion {
            if (it == null && autoplay) {
                autoplay()
            }
        }
    }

    private fun populateList(result: SearchResult) {
        val list = mutableListOf<Identifiable>()

        val artists = result.artists
        if (artists.isNotEmpty()) {

            list.add(DividerBinder.Divider(R.string.search_artists))
            list.addAll(artists)
            if (searchResult!!.artists.size > artists.size) {
                list.add(MoreButton(0, ::expandArtists))
            }
        }
        val albums = result.albums
        if (albums.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_albums))
            list.addAll(albums)
            if (searchResult!!.albums.size > albums.size) {
                list.add(MoreButton(1, ::expandAlbums))
            }
        }
        val songs = result.songs
        if (songs.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_songs))
            list.addAll(songs)
            if (searchResult!!.songs.size > songs.size) {
                list.add(MoreButton(2, ::expandSongs))
            }
        }

        // Show/hide the empty text view
        emptyView.isVisible = list.isEmpty()

        viewAdapter.submitList(list)
    }

    private fun expandArtists() {
        populateList(listModel.trimResultLength(searchResult!!, maxArtists = Int.MAX_VALUE))
    }

    private fun expandAlbums() {
        populateList(listModel.trimResultLength(searchResult!!, maxAlbums = Int.MAX_VALUE))
    }

    private fun expandSongs() {
        populateList(listModel.trimResultLength(searchResult!!, maxSongs = Int.MAX_VALUE))
    }

    private fun onArtistSelected(item: ArtistOrIndex) {
        val bundle = Bundle()

        // Common arguments
        bundle.putString(Constants.INTENT_ID, item.id)
        bundle.putString(Constants.INTENT_NAME, item.name)
        bundle.putString(Constants.INTENT_PARENT_ID, item.id)
        bundle.putBoolean(Constants.INTENT_ARTIST, (item is Artist))

        // Check type
        if (item is Index) {
            findNavController().navigate(R.id.searchToTrackCollection, bundle)
        } else {
            bundle.putString(Constants.INTENT_ALBUM_LIST_TYPE, Constants.ALBUMS_OF_ARTIST)
            bundle.putString(Constants.INTENT_ALBUM_LIST_TITLE, item.name)
            bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, 1000)
            bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0)
            findNavController().navigate(R.id.searchToAlbumsList, bundle)
        }
    }

    private fun onAlbumSelected(album: Album, autoplay: Boolean) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_ID, album.id)
        bundle.putString(Constants.INTENT_NAME, album.title)
        bundle.putBoolean(Constants.INTENT_IS_ALBUM, album.isDirectory)
        bundle.putBoolean(Constants.INTENT_AUTOPLAY, autoplay)
        Navigation.findNavController(requireView()).navigate(R.id.searchToTrackCollection, bundle)
    }

    private fun onSongSelected(song: Track, append: Boolean) {
        if (!append) {
            mediaPlayerController.clear()
        }
        mediaPlayerController.addToPlaylist(
            listOf(song),
            save = false,
            autoPlay = false,
            playNext = false,
            shuffle = false,
            newPlaylist = false
        )
        mediaPlayerController.play(mediaPlayerController.playlistSize - 1)
        toast(context, resources.getQuantityString(R.plurals.select_album_n_songs_added, 1, 1))
    }

    private fun onVideoSelected(track: Track) {
        playVideo(requireContext(), track)
    }

    private fun autoplay() {
        if (searchResult!!.songs.isNotEmpty()) {
            onSongSelected(searchResult!!.songs[0], false)
        } else if (searchResult!!.albums.isNotEmpty()) {
            onAlbumSelected(searchResult!!.albums[0], true)
        }
    }

    override fun onItemClick(item: Identifiable) {
        when (item) {
            is ArtistOrIndex -> {
                onArtistSelected(item)
            }
            is Track -> {
                if (item.isVideo) {
                    onVideoSelected(item)
                } else {
                    onSongSelected(item, true)
                }
            }
            is Album -> {
                onAlbumSelected(item, false)
            }
        }
    }

    @Suppress("LongMethod")
    override fun onContextMenuItemSelected(menuItem: MenuItem, item: Identifiable): Boolean {
        val isArtist = (item is Artist)

        val found = EntryListFragment.handleContextMenu(
            menuItem,
            item,
            isArtist,
            downloadHandler,
            this
        )

        if (found || item !is DownloadFile) return true

        val songs = mutableListOf<Track>()

        when (menuItem.itemId) {
            R.id.song_menu_play_now -> {
                songs.add(item.track)
                downloadHandler.download(
                    fragment = this,
                    append = false,
                    save = false,
                    autoPlay = true,
                    playNext = false,
                    shuffle = false,
                    songs = songs
                )
            }
            R.id.song_menu_play_next -> {
                songs.add(item.track)
                downloadHandler.download(
                    fragment = this,
                    append = true,
                    save = false,
                    autoPlay = false,
                    playNext = true,
                    shuffle = false,
                    songs = songs
                )
            }
            R.id.song_menu_play_last -> {
                songs.add(item.track)
                downloadHandler.download(
                    fragment = this,
                    append = true,
                    save = false,
                    autoPlay = false,
                    playNext = false,
                    shuffle = false,
                    songs = songs
                )
            }
            R.id.song_menu_pin -> {
                songs.add(item.track)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned,
                        songs.size,
                        songs.size
                    )
                )
                downloadBackground(true, songs)
            }
            R.id.song_menu_download -> {
                songs.add(item.track)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloaded,
                        songs.size,
                        songs.size
                    )
                )
                downloadBackground(false, songs)
            }
            R.id.song_menu_unpin -> {
                songs.add(item.track)
                toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_unpinned,
                        songs.size,
                        songs.size
                    )
                )
                mediaPlayerController.unpin(songs)
            }
            R.id.song_menu_share -> {
                songs.add(item.track)
                shareHandler.createShare(this, songs, searchRefresh, cancellationToken!!)
            }
        }

        return true
    }

    companion object {
        var DEFAULT_ARTISTS = Settings.defaultArtists
        var DEFAULT_ALBUMS = Settings.defaultAlbums
        var DEFAULT_SONGS = Settings.defaultSongs
    }
}
