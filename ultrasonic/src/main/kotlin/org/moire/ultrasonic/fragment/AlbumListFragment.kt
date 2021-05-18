package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.Constants

/**
 * Displays a list of Albums from the media library
 * TODO: Check refresh is working
 */
@KoinApiExtension
class AlbumListFragment : GenericListFragment<MusicDirectory.Entry, AlbumRowAdapter>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: AlbumListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.generic_list

    /**
     * The id of the refresh view
     */
    override val refreshListId: Int = R.id.generic_list_refresh

    /**
     * The id of the RecyclerView
     */
    override val recyclerViewId = R.id.generic_list_recycler

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    override val itemClickTarget: Int = R.id.trackCollectionFragment

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(args: Bundle?): LiveData<List<MusicDirectory.Entry>> {
        if (args == null) throw IllegalArgumentException("Required arguments are missing")

        val refresh = args.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH)

        return listModel.getAlbumList(refresh, refreshListView!!, args)
    }

    /**
     * Provide the Adapter for the RecyclerView with a lazy delegate
     */
    override val viewAdapter: AlbumRowAdapter by lazy {
        AlbumRowAdapter(
            liveDataItems.value ?: listOf(),
            { entry -> onItemClick(entry) },
            { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
            imageLoaderProvider.getImageLoader(),
            onMusicFolderUpdate
        )
    }

    val newBundleClone: Bundle
        get() = arguments?.clone() as Bundle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Attach our onScrollListener
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            val scrollListener = object : EndlessScrollListener(viewManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                    // Triggered only when new data needs to be appended to the list
                    // Add whatever code is needed to append new items to the bottom of the list
                    val appendArgs = newBundleClone
                    appendArgs.putBoolean(Constants.INTENT_EXTRA_NAME_APPEND, true)
                    getLiveData(appendArgs)
                }
            }
            addOnScrollListener(scrollListener)
        }
    }

    override fun onItemClick(item: MusicDirectory.Entry) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, item.id)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, item.isDirectory)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, item.title)
        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, item.parent)
        findNavController().navigate(itemClickTarget, bundle)
    }
}
