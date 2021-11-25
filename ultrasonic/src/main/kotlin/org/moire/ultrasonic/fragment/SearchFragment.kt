package org.moire.ultrasonic.fragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ListAdapter
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.DividerBinder
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.SearchListModel
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer.Companion.playVideo
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.view.ArtistAdapter
import timber.log.Timber

/**
 * Initiates a search on the media library and displays the results
 */
class SearchFragment : MultiListFragment<Identifiable>(), KoinComponent {
    private var artistsHeading: View? = null
    private var albumsHeading: View? = null
    private var songsHeading: View? = null
    private var notFound: TextView? = null
    private var moreArtistsButton: View? = null
    private var moreAlbumsButton: View? = null
    private var moreSongsButton: View? = null
    private var searchResult: SearchResult? = null
    private var artistAdapter: ArtistAdapter? = null
    private var moreArtistsAdapter: ListAdapter? = null
    private var moreAlbumsAdapter: ListAdapter? = null
    private var moreSongsAdapter: ListAdapter? = null
    private var searchRefresh: SwipeRefreshLayout? = null

    private val mediaPlayerController: MediaPlayerController by inject()

    private val shareHandler: ShareHandler by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()

    private var cancellationToken: CancellationToken? = null

    override val listModel: SearchListModel by viewModels()

    override val recyclerViewId = R.id.search_list

    override val mainLayout: Int = R.layout.search

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()
        setTitle(this, R.string.search_title)
        setHasOptionsMenu(true)

        val buttons = LayoutInflater.from(context).inflate(
            R.layout.search_buttons,
            listView, false
        )

        if (buttons != null) {
            artistsHeading = buttons.findViewById(R.id.search_artists)
            albumsHeading = buttons.findViewById(R.id.search_albums)
            songsHeading = buttons.findViewById(R.id.search_songs)
            notFound = buttons.findViewById(R.id.search_not_found)
            moreArtistsButton = buttons.findViewById(R.id.search_more_artists)
            moreAlbumsButton = buttons.findViewById(R.id.search_more_albums)
            moreSongsButton = buttons.findViewById(R.id.search_more_songs)
        }

        listModel.searchResult.observe(
            viewLifecycleOwner,
            {
                if (it != null) populateList(it)
            }
        )

        searchRefresh = view.findViewById(R.id.search_entries_refresh)
        searchRefresh!!.isEnabled = false

//        list.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>, view1: View, position: Int, id: Long ->
//            if (view1 === moreArtistsButton) {
//                expandArtists()
//            } else if (view1 === moreAlbumsButton) {
//                expandAlbums()
//            } else if (view1 === moreSongsButton) {
//                expandSongs()
//            } else {
//                val item = parent.getItemAtPosition(position)
//                if (item is Artist) {
//                    onArtistSelected(item)
//                } else if (item is MusicDirectory.Entry) {
//                    val entry = item
//                    if (entry.isDirectory) {
//                        onAlbumSelected(entry, false)
//                    } else if (entry.isVideo) {
//                        onVideoSelected(entry)
//                    } else {
//                        onSongSelected(entry, true)
//                    }
//                }
//            }
//        })

        registerForContextMenu(listView!!)

        viewAdapter.register(
            TrackViewBinder(
                checkable = false,
                draggable = false,
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner
            )
        )

        viewAdapter.register(
            ArtistRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
                imageLoaderProvider.getImageLoader()
            )
        )

        viewAdapter.register(
            DividerBinder()
        )

        // Fragment was started with a query (e.g. from voice search), try to execute search right away
        val arguments = arguments
        if (arguments != null) {
            val query = arguments.getString(Constants.INTENT_EXTRA_NAME_QUERY)
            val autoPlay = arguments.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
            if (query != null) {
                return search(query, autoPlay)
            }
        }

        // Fragment was started from the Menu, create empty list
        populateList(SearchResult())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val activity = activity ?: return
        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        inflater.inflate(R.menu.search, menu)
        val searchItem = menu.findItem(R.id.search_item)
        val searchView = searchItem.actionView as SearchView
        val searchableInfo = searchManager.getSearchableInfo(requireActivity().componentName)
        searchView.setSearchableInfo(searchableInfo)

        val arguments = arguments
        val autoPlay =
            arguments != null && arguments.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
        val query = arguments?.getString(Constants.INTENT_EXTRA_NAME_QUERY)
        // If started with a query, enter it to the searchView
        if (query != null) {
            searchView.setQuery(query, false)
            searchView.clearFocus()
        }
        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return true
            }

            override fun onSuggestionClick(position: Int): Boolean {
                Timber.d("onSuggestionClick: %d", position)
                val cursor = searchView.suggestionsAdapter.cursor
                cursor.moveToPosition(position)

                // TODO: Try to do something with this magic const:
                //  2 is the index of col containing suggestion name.
                val suggestion = cursor.getString(2)
                searchView.setQuery(suggestion, true)
                return true
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                Timber.d("onQueryTextSubmit: %s", query)
                searchView.clearFocus()
                search(query, autoPlay)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })
        searchView.setIconifiedByDefault(false)
        searchItem.expandActionView()
    }

    // FIXME
    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        if (activity == null) return
        val info = menuInfo as AdapterContextMenuInfo?
//        val selectedItem = list!!.getItemAtPosition(info!!.position)
//        val isArtist = selectedItem is Artist
//        val isAlbum = selectedItem is MusicDirectory.Entry && selectedItem.isDirectory
//        val inflater = requireActivity().menuInflater
//        if (!isArtist && !isAlbum) {
//            inflater.inflate(R.menu.select_song_context, menu)
//        } else {
//            inflater.inflate(R.menu.generic_context_menu, menu)
//        }
//        val shareButton = menu.findItem(R.id.menu_item_share)
//        val downloadMenuItem = menu.findItem(R.id.menu_download)
//        if (downloadMenuItem != null) {
//            downloadMenuItem.isVisible = !isOffline()
//        }
//        if (isOffline() || isArtist) {
//            if (shareButton != null) {
//                shareButton.isVisible = false
//            }
//        }
    }

    // FIXME
    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        val info = menuItem.menuInfo as AdapterContextMenuInfo
//        val selectedItem = list!!.getItemAtPosition(info.position)
//        val artist = if (selectedItem is Artist) selectedItem else null
//        val entry = if (selectedItem is MusicDirectory.Entry) selectedItem else null
//        var entryId: String? = null
//        if (entry != null) {
//            entryId = entry.id
//        }
//        val id = artist?.id ?: entryId ?: return true
//        var songs: MutableList<MusicDirectory.Entry?> = ArrayList(1)
//        val itemId = menuItem.itemId
//        if (itemId == R.id.menu_play_now) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                false,
//                false,
//                true,
//                false,
//                false,
//                false,
//                false,
//                false
//            )
//        } else if (itemId == R.id.menu_play_next) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                false,
//                true,
//                false,
//                true,
//                false,
//                true,
//                false,
//                false
//            )
//        } else if (itemId == R.id.menu_play_last) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                false,
//                true,
//                false,
//                false,
//                false,
//                false,
//                false,
//                false
//            )
//        } else if (itemId == R.id.menu_pin) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                true,
//                true,
//                false,
//                false,
//                false,
//                false,
//                false,
//                false
//            )
//        } else if (itemId == R.id.menu_unpin) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                false,
//                false,
//                false,
//                false,
//                false,
//                false,
//                true,
//                false
//            )
//        } else if (itemId == R.id.menu_download) {
//            downloadHandler.downloadRecursively(
//                this,
//                id,
//                false,
//                false,
//                false,
//                false,
//                true,
//                false,
//                false,
//                false
//            )
//        } else if (itemId == R.id.song_menu_play_now) {
//            if (entry != null) {
//                songs = ArrayList(1)
//                songs.add(entry)
//                downloadHandler.download(this, false, false, true, false, false, songs)
//            }
//        } else if (itemId == R.id.song_menu_play_next) {
//            if (entry != null) {
//                songs = ArrayList(1)
//                songs.add(entry)
//                downloadHandler.download(this, true, false, false, true, false, songs)
//            }
//        } else if (itemId == R.id.song_menu_play_last) {
//            if (entry != null) {
//                songs = ArrayList(1)
//                songs.add(entry)
//                downloadHandler.download(this, true, false, false, false, false, songs)
//            }
//        } else if (itemId == R.id.song_menu_pin) {
//            if (entry != null) {
//                songs.add(entry)
//                toast(
//                    context,
//                    resources.getQuantityString(
//                        R.plurals.select_album_n_songs_pinned,
//                        songs.size,
//                        songs.size
//                    )
//                )
//                downloadBackground(true, songs)
//            }
//        } else if (itemId == R.id.song_menu_download) {
//            if (entry != null) {
//                songs.add(entry)
//                toast(
//                    context,
//                    resources.getQuantityString(
//                        R.plurals.select_album_n_songs_downloaded,
//                        songs.size,
//                        songs.size
//                    )
//                )
//                downloadBackground(false, songs)
//            }
//        } else if (itemId == R.id.song_menu_unpin) {
//            if (entry != null) {
//                songs.add(entry)
//                toast(
//                    context,
//                    resources.getQuantityString(
//                        R.plurals.select_album_n_songs_unpinned,
//                        songs.size,
//                        songs.size
//                    )
//                )
//                mediaPlayerController.unpin(songs)
//            }
//        } else if (itemId == R.id.menu_item_share) {
//            if (entry != null) {
//                songs = ArrayList(1)
//                songs.add(entry)
//                shareHandler.createShare(this, songs, searchRefresh, cancellationToken!!)
//            }
//            return super.onContextItemSelected(menuItem)
//        } else {
//            return super.onContextItemSelected(menuItem)
//        }
        return true
    }

    // OK!
    override fun onDestroyView() {
        cancellationToken?.cancel()
        super.onDestroyView()
    }

    // OK!
    private fun downloadBackground(save: Boolean, songs: List<MusicDirectory.Entry?>) {
        val onValid = Runnable {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.downloadBackground(songs, save)
        }
        onValid.run()
    }

    private fun search(query: String, autoplay: Boolean) {
        // FIXME support autoplay
        listModel.viewModelScope.launch(CommunicationError.getHandler(context)) {
            listModel.search(query)

        }
    }

    private fun populateList(result: SearchResult) {
        val searchResult = listModel.trimResultLength(result)

        val list = mutableListOf<Identifiable>()

        val artists = searchResult.artists
        if (artists.isNotEmpty()) {

            list.add(DividerBinder.Divider(R.string.search_artists))
            list.addAll(artists)
            if (artists.size > DEFAULT_ARTISTS) {
                // FIXME
                // list.add((moreArtistsButton, true)
            }
        }
        val albums = searchResult.albums
        if (albums.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_albums))
            list.addAll(albums)
            // mergeAdapter!!.addAdapter(albumAdapter)
//            if (albums.size > DEFAULT_ALBUMS) {
//                moreAlbumsAdapter = mergeAdapter!!.addView(moreAlbumsButton, true)
//            }
        }
        val songs = searchResult.songs
        if (songs.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_albums))
            list.addAll(songs)
//            if (songs.size > DEFAULT_SONGS) {
//                moreSongsAdapter = mergeAdapter!!.addView(moreSongsButton, true)
//            }
        }

        // FIXME
        if (list.isEmpty()) {
            // mergeAdapter!!.addView(notFound, false)
        }

        viewAdapter.submitList(list)
    }

//    private fun expandArtists() {
//        artistAdapter!!.clear()
//        for (artist in searchResult!!.artists) {
//            artistAdapter!!.add(artist)
//        }
//        artistAdapter!!.notifyDataSetChanged()
//        mergeAdapter!!.removeAdapter(moreArtistsAdapter)
//        mergeAdapter!!.notifyDataSetChanged()
//    }
//
//    private fun expandAlbums() {
//        albumAdapter!!.clear()
//        for (album in searchResult!!.albums) {
//            albumAdapter!!.add(album)
//        }
//        albumAdapter!!.notifyDataSetChanged()
//        mergeAdapter!!.removeAdapter(moreAlbumsAdapter)
//        mergeAdapter!!.notifyDataSetChanged()
//    }
//
//    private fun expandSongs() {
//        songAdapter!!.clear()
//        for (song in searchResult!!.songs) {
//            songAdapter!!.add(song)
//        }
//        songAdapter!!.notifyDataSetChanged()
//        mergeAdapter!!.removeAdapter(moreSongsAdapter)
//        mergeAdapter!!.notifyDataSetChanged()
//    }
//
//    private fun onArtistSelected(artist: Artist) {
//        val bundle = Bundle()
//        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, artist.id)
//        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.id)
//        Navigation.findNavController(requireView()).navigate(R.id.searchToSelectAlbum, bundle)
//    }

    private fun onAlbumSelected(album: MusicDirectory.Entry, autoplay: Boolean) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, album.id)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, album.title)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, album.isDirectory)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, autoplay)
        Navigation.findNavController(requireView()).navigate(R.id.searchToSelectAlbum, bundle)
    }

    private fun onSongSelected(song: MusicDirectory.Entry, append: Boolean) {
        if (!append) {
            mediaPlayerController.clear()
        }
        mediaPlayerController.addToPlaylist(listOf(song), false, false, false, false, false)
        mediaPlayerController.play(mediaPlayerController.playlistSize - 1)
        toast(context, resources.getQuantityString(R.plurals.select_album_n_songs_added, 1, 1))
    }

    private fun onVideoSelected(entry: MusicDirectory.Entry) {
        playVideo(requireContext(), entry)
    }

    private fun autoplay() {
        if (searchResult!!.songs.isNotEmpty()) {
            onSongSelected(searchResult!!.songs[0], false)
        } else if (searchResult!!.albums.isNotEmpty()) {
            onAlbumSelected(searchResult!!.albums[0], true)
        }
    }

    companion object {
        var DEFAULT_ARTISTS = Settings.defaultArtists
        var DEFAULT_ALBUMS = Settings.defaultAlbums
        var DEFAULT_SONGS = Settings.defaultSongs
    }

    // FIXME!!
    override fun getLiveData(args: Bundle?): LiveData<List<Identifiable>> {
        return MutableLiveData(listOf())
    }

    // FIXME
    override val itemClickTarget: Int = 0

    // FIXME
    override fun onContextMenuItemSelected(menuItem: MenuItem, item: Identifiable): Boolean {
        return true
    }

    // FIXME
    override fun onItemClick(item: Identifiable) {
    }
}
