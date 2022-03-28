/*
 * AlbumListFragment.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowBinder
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.AlbumListModel
import org.moire.ultrasonic.util.Constants

/**
 * Displays a list of Albums from the media library
 */
class AlbumListFragment : EntryListFragment<Album>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: AlbumListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_generic

    /**
     * Whether to refresh the data onViewCreated
     */
    override val refreshOnCreation: Boolean = false

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(
        args: Bundle?,
        refresh: Boolean
    ): LiveData<List<Album>> {
        if (args == null) throw IllegalArgumentException("Required arguments are missing")

        val refresh2 = args.getBoolean(Constants.INTENT_REFRESH) || refresh
        val append = args.getBoolean(Constants.INTENT_APPEND)

        return listModel.getAlbumList(refresh2 or append, refreshListView!!, args)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Attach our onScrollListener
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            val scrollListener = object : EndlessScrollListener(viewManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                    // Triggered only when new data needs to be appended to the list
                    // Add whatever code is needed to append new items to the bottom of the list
                    val appendArgs = getArgumentsClone()
                    appendArgs.putBoolean(Constants.INTENT_APPEND, true)
                    getLiveData(appendArgs)
                }
            }
            addOnScrollListener(scrollListener)
        }

        viewAdapter.register(
            AlbumRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
                imageLoaderProvider.getImageLoader(),
                context = requireContext()
            )
        )

        emptyTextView.setText(R.string.select_album_empty)
    }

    override fun onItemClick(item: Album) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_ID, item.id)
        bundle.putBoolean(Constants.INTENT_IS_ALBUM, item.isDirectory)
        bundle.putString(Constants.INTENT_NAME, item.title)
        bundle.putString(Constants.INTENT_PARENT_ID, item.parent)
        findNavController().navigate(R.id.trackCollectionFragment, bundle)
    }
}
