package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
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
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util

class SelectArtistFragment : Fragment() {
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val downloadHandler: DownloadHandler by inject()

    private var refreshArtistListView: SwipeRefreshLayout? = null
    private var artistListView: RecyclerView? = null
    private var musicFolders: List<MusicFolder>? = null
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var viewAdapter: ArtistRowAdapter

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.select_artist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshArtistListView = view.findViewById(R.id.select_artist_refresh)
        refreshArtistListView!!.setOnRefreshListener {
            artistListModel.refresh(refreshArtistListView!!)
        }

        val shouldShowHeader = (!ActiveServerProvider.isOffline(this.context) && !Util.getShouldUseId3Tags(this.context))

        val title = arguments?.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE)

        if (title == null) {
            setTitle(
                this,
                if (ActiveServerProvider.isOffline(this.context)) R.string.music_library_label_offline
                else R.string.music_library_label
            )
        } else {
            setTitle(this, title)
        }

        musicFolders = null

        val refresh = arguments?.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH) ?: false

        artistListModel.getMusicFolders()
            .observe(
                viewLifecycleOwner,
                Observer { changedFolders ->
                    if (changedFolders != null) {
                        musicFolders = changedFolders
                        viewAdapter.setFolderName(getMusicFolderName(changedFolders))
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
            getText(R.string.select_artist_all_folders).toString(),
            shouldShowHeader,
            { artist -> onItemClick(artist) },
            { menuItem, artist -> onArtistMenuItemSelected(menuItem, artist) },
            { onFolderClick(it) },
            imageLoaderProvider.getImageLoader()
        )

        artistListView = view.findViewById<RecyclerView>(R.id.select_artist_list).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun getMusicFolderName(musicFolders: List<MusicFolder>): String {
        val musicFolderId = activeServerProvider.getActiveServer().musicFolderId
        if (musicFolderId != null && musicFolderId != "") {
            for ((id, name) in musicFolders) {
                if (id == musicFolderId) {
                    return name
                }
            }
        }
        return getText(R.string.select_artist_all_folders).toString()
    }

    private fun onItemClick(artist: Artist) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, artist.id)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.name)
        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, artist.id)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true)
        findNavController().navigate(R.id.selectArtistToSelectAlbum, bundle)
    }

    private fun onFolderClick(view: View) {
        val popup = PopupMenu(this.context, view)

        val musicFolderId = activeServerProvider.getActiveServer().musicFolderId
        var menuItem = popup.menu.add(
            MENU_GROUP_MUSIC_FOLDER, -1, 0, R.string.select_artist_all_folders
        )
        if (musicFolderId == null || musicFolderId.isEmpty()) {
            menuItem.isChecked = true
        }
        if (musicFolders != null) {
            for (i in musicFolders!!.indices) {
                val (id, name) = musicFolders!![i]
                menuItem = popup.menu.add(MENU_GROUP_MUSIC_FOLDER, i, i + 1, name)
                if (id == musicFolderId) {
                    menuItem.isChecked = true
                }
            }
        }
        popup.menu.setGroupCheckable(MENU_GROUP_MUSIC_FOLDER, true, true)

        popup.setOnMenuItemClickListener { item -> onFolderMenuItemSelected(item) }
        popup.show()
    }

    private fun onArtistMenuItemSelected(menuItem: MenuItem, artist: Artist): Boolean {
        when (menuItem.itemId) {
            R.id.artist_menu_play_now ->
                downloadHandler.downloadRecursively(this, artist.id, false, false, true, false, false, false, false, true)
            R.id.artist_menu_play_next ->
                downloadHandler.downloadRecursively(this, artist.id, false, false, true, true, false, true, false, true)
            R.id.artist_menu_play_last ->
                downloadHandler.downloadRecursively(this, artist.id, false, true, false, false, false, false, false, true)
            R.id.artist_menu_pin ->
                downloadHandler.downloadRecursively(this, artist.id, true, true, false, false, false, false, false, true)
            R.id.artist_menu_unpin ->
                downloadHandler.downloadRecursively(this, artist.id, false, false, false, false, false, false, true, true)
            R.id.artist_menu_download ->
                downloadHandler.downloadRecursively(this, artist.id, false, false, false, false, true, false, false, true)
        }
        return true
    }

    private fun onFolderMenuItemSelected(menuItem: MenuItem): Boolean {
        val selectedFolder = if (menuItem.itemId == -1) null else musicFolders!![menuItem.itemId]
        val musicFolderId = selectedFolder?.id
        val musicFolderName = selectedFolder?.name
            ?: getString(R.string.select_artist_all_folders)
        if (!ActiveServerProvider.isOffline(this.context)) {
            val currentSetting = activeServerProvider.getActiveServer()
            currentSetting.musicFolderId = musicFolderId
            serverSettingsModel.updateItem(currentSetting)
        }
        viewAdapter.setFolderName(musicFolderName)
        artistListModel.refresh(refreshArtistListView!!)
        return true
    }

    companion object {
        private const val MENU_GROUP_MUSIC_FOLDER = 10
    }
}