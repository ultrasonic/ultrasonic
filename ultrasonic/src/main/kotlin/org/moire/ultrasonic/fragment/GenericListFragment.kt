package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.GenericEntry
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SelectMusicFolderView

/**
 * An abstract Model, which can be extended to display a list of items of type T from the API
 * @param T: The type of data which will be used (must extend GenericEntry)
 * @param TA: The Adapter to use (must extend GenericRowAdapter)
 */
@KoinApiExtension
abstract class GenericListFragment<T : GenericEntry, TA : GenericRowAdapter<T>> : Fragment() {
    internal val activeServerProvider: ActiveServerProvider by inject()
    internal val serverSettingsModel: ServerSettingsModel by viewModel()
    internal val imageLoaderProvider: ImageLoaderProvider by inject()
    protected val downloadHandler: DownloadHandler by inject()
    protected var refreshListView: SwipeRefreshLayout? = null
    internal var listView: RecyclerView? = null
    internal lateinit var viewManager: LinearLayoutManager
    internal var selectFolderHeader: SelectMusicFolderView? = null

    /**
     * The Adapter for the RecyclerView
     * Recommendation: Implement this as a lazy delegate
     */
    internal abstract val viewAdapter: TA

    /**
     * The ViewModel to use to get the data
     */
    open val listModel: GenericListModel by viewModels()

    /**
     * The LiveData containing the list provided by the model
     * Implement this as a getter
     */
    internal lateinit var liveDataItems: LiveData<List<T>>

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    abstract fun getLiveData(args: Bundle? = null): LiveData<List<T>>

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    protected abstract val itemClickTarget: Int

    /**
     * The id of the RecyclerView
     */
    protected abstract val recyclerViewId: Int

    /**
     * The id of the main layout
     */
    abstract val mainLayout: Int

    /**
     * The id of the refresh view
     */
    abstract val refreshListId: Int

    /**
     * The observer to be called if the available music folders have changed
     */
    @Suppress("CommentOverPrivateProperty")
    private val musicFolderObserver = { folders: List<MusicFolder> ->
        viewAdapter.setFolderList(folders, listModel.activeServer.musicFolderId)
        Unit
    }

    /**
     * What to do when the user has modified the folder filter
     */
    val onMusicFolderUpdate = { selectedFolderId: String? ->
        if (!listModel.isOffline()) {
            val currentSetting = listModel.activeServer
            currentSetting.musicFolderId = selectedFolderId
            serverSettingsModel.updateItem(currentSetting)
        }
        viewAdapter.notifyDataSetChanged()
        listModel.refresh(refreshListView!!, arguments)
    }

    /**
     * Whether to show the folder selector
     */
    fun showFolderHeader(): Boolean {
        return listModel.showSelectFolderHeader(arguments) &&
            !listModel.isOffline() && !Util.getShouldUseId3Tags()
    }

    fun setTitle(title: String?) {
        if (title == null) {
            FragmentTitle.setTitle(
                this,
                if (listModel.isOffline())
                    R.string.music_library_label_offline
                else R.string.music_library_label
            )
        } else {
            FragmentTitle.setTitle(this, title)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the title if available
        setTitle(arguments?.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE))

        // Setup refresh handler
        refreshListView = view.findViewById(refreshListId)
        refreshListView?.setOnRefreshListener {
            listModel.refresh(refreshListView!!, arguments)
        }

        // Populate the LiveData. This starts an API request in most cases
        liveDataItems = getLiveData(arguments)

        // Register an observer to update our UI when the data changes
        liveDataItems.observe(viewLifecycleOwner, { newItems -> viewAdapter.setData(newItems) })

        // Setup the Music folder handling
        listModel.getMusicFolders().observe(viewLifecycleOwner, musicFolderObserver)

        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        // Configure whether to show the folder header
        viewAdapter.folderHeaderEnabled = showFolderHeader()
    }

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
        return inflater.inflate(mainLayout, container, false)
    }

    @Suppress("LongMethod")
    fun onContextMenuItemSelected(menuItem: MenuItem, item: T): Boolean {
        val isArtist = (item is Artist)

        when (menuItem.itemId) {
            R.id.menu_play_now ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = false,
                    append = false,
                    autoPlay = true,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = isArtist
                )
            R.id.menu_play_next ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = false,
                    append = false,
                    autoPlay = true,
                    shuffle = true,
                    background = false,
                    playNext = true,
                    unpin = false,
                    isArtist = isArtist
                )
            R.id.menu_play_last ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = false,
                    append = true,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = isArtist
                )
            R.id.menu_pin ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = true,
                    append = true,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = false,
                    isArtist = isArtist
                )
            R.id.menu_unpin ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = false,
                    append = false,
                    autoPlay = false,
                    shuffle = false,
                    background = false,
                    playNext = false,
                    unpin = true,
                    isArtist = isArtist
                )
            R.id.menu_download ->
                downloadHandler.downloadRecursively(
                    this,
                    item.id,
                    save = false,
                    append = false,
                    autoPlay = false,
                    shuffle = false,
                    background = true,
                    playNext = false,
                    unpin = false,
                    isArtist = isArtist
                )
        }
        return true
    }

    open fun onItemClick(item: T) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, item.id)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, item.name)
        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, item.id)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, (item is Artist))
        findNavController().navigate(itemClickTarget, bundle)
    }
}
