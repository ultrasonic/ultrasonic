package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SelectMusicFolderView

/**
 * Displays the list of Artists from the media library
 */
class SelectArtistFragment : Fragment() {
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val downloadHandler: DownloadHandler by inject()

    private var refreshArtistListView: SwipeRefreshLayout? = null
    private var artistListView: RecyclerView? = null
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var viewAdapter: ArtistRowAdapter
    private var selectFolderHeader: SelectMusicFolderView? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_artist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshArtistListView = view.findViewById(R.id.select_artist_refresh)
        refreshArtistListView!!.setOnRefreshListener {
            artistListModel.refresh(refreshArtistListView!!)
        }

        if (!ActiveServerProvider.isOffline(this.context) &&
            !Util.getShouldUseId3Tags()
        ) {
            selectFolderHeader = SelectMusicFolderView(
                requireContext(), view as ViewGroup,
                { selectedFolderId ->
                    if (!ActiveServerProvider.isOffline(context)) {
                        val currentSetting = activeServerProvider.getActiveServer()
                        currentSetting.musicFolderId = selectedFolderId
                        serverSettingsModel.updateItem(currentSetting)
                    }
                    viewAdapter.notifyDataSetChanged()
                    artistListModel.refresh(refreshArtistListView!!)
                }
            )
        }

        val title = arguments?.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE)

        if (title == null) {
            setTitle(
                this,
                if (ActiveServerProvider.isOffline(this.context))
                    R.string.music_library_label_offline
                else R.string.music_library_label
            )
        } else {
            setTitle(this, title)
        }

        val refresh = arguments?.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH) ?: false

        artistListModel.getMusicFolders()
            .observe(
                viewLifecycleOwner,
                Observer { changedFolders ->
                    if (changedFolders != null) {
                        viewAdapter.notifyDataSetChanged()
                        selectFolderHeader!!.setData(
                            activeServerProvider.getActiveServer().musicFolderId,
                            changedFolders
                        )
                    }
                }
            )

        val artists = artistListModel.getArtists(refresh, refreshArtistListView!!)
        artists.observe(
            viewLifecycleOwner, Observer { changedArtists -> viewAdapter.setData(changedArtists) }
        )

        viewManager = LinearLayoutManager(this.context)
        viewAdapter = ArtistRowAdapter(
            artists.value ?: listOf(),
            selectFolderHeader,
            { artist -> onItemClick(artist) },
            { menuItem, artist -> onArtistMenuItemSelected(menuItem, artist) },
            imageLoaderProvider.getImageLoader()
        )

        artistListView = view.findViewById<RecyclerView>(R.id.select_artist_list).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun onItemClick(artist: Artist) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, artist.id)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.name)
        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, artist.id)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
        findNavController().navigate(R.id.selectArtistToSelectAlbum, bundle)
    }

    private fun onArtistMenuItemSelected(menuItem: MenuItem, artist: Artist): Boolean {
        when (menuItem.itemId) {
            R.id.artist_menu_play_now ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = false,
                    append = false,
                    autoPlay = true,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = true
                )
            R.id.artist_menu_play_next ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = false,
                    append = false,
                    autoPlay = true,
                    shuffle = true,
                    background = false,
                    playNext = true,
                    unpin = false,
                    isArtist = true
                )
            R.id.artist_menu_play_last ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = false,
                    append = true,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = true
                )
            R.id.artist_menu_pin ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = true,
                    append = true,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = true
                )
            R.id.artist_menu_unpin ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = false,
                    append = false,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = true,
                    isArtist = true
                )
            R.id.artist_menu_download ->
                downloadHandler.downloadRecursively(
                    this,
                    artist.id,
                    save = false,
                    append = false,
                    autoPlay = false,
                    shuffle = false,
                    background = true,
                    playNext = false,
                    unpin = false,
                    isArtist = true
                )
        }
        return true
    }
}
