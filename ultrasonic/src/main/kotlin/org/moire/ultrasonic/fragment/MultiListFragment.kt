package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.model.GenericListModel
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SelectMusicFolderView

/**
 * An abstract Model, which can be extended to display a list of items of type T from the API
 * @param T: The type of data which will be used (must extend GenericEntry)
 */
abstract class MultiListFragment<T : Identifiable> : Fragment() {
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
    internal val viewAdapter: BaseAdapter<Identifiable> by lazy {
        BaseAdapter()
    }

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
    open fun getLiveData(args: Bundle? = null): LiveData<List<T>> {
        return MutableLiveData(listOf())
    }

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    protected abstract val itemClickTarget: Int

    /**
     * The id of the main layout
     */
    open val mainLayout: Int = R.layout.generic_list

    /**
     * The id of the refresh view
     */
    open val refreshListId: Int = R.id.generic_list_refresh

    /**
     * The id of the RecyclerView
     */
    open val recyclerViewId = R.id.generic_list_recycler

    open fun setTitle(title: String?) {
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
        liveDataItems.observe(
            viewLifecycleOwner,
            {
                newItems ->
                viewAdapter.submitList(newItems)
            }
        )

        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        // Configure whether to show the folder header
        // viewAdapter.folderHeaderEnabled = showFolderHeader()
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

    abstract fun onContextMenuItemSelected(menuItem: MenuItem, item: T): Boolean

    abstract fun onItemClick(item: T)

    fun getArgumentsClone(): Bundle {
        var bundle: Bundle

        try {
            bundle = arguments?.clone() as Bundle
        } catch (ignored: Exception) {
           bundle = Bundle()
        }

        return bundle
    }
}

