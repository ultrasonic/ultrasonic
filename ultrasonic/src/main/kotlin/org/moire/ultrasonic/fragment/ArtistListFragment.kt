package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.util.Constants

/**
 * Displays the list of Artists or Indexes (folders) from the media library
 */
class ArtistListFragment : EntryListFragment<ArtistOrIndex>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: ArtistListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout = R.layout.list_layout_generic

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(args: Bundle?, refresh: Boolean): LiveData<List<ArtistOrIndex>> {
        val refresh2 = args?.getBoolean(Constants.INTENT_REFRESH) ?: false || refresh
        return listModel.getItems(refresh2, refreshListView!!)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewAdapter.register(
            ArtistRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
                imageLoaderProvider.getImageLoader()
            )
        )
    }

    /**
     * There are different targets depending on what list we show.
     * If we are showing indexes, we need to go to TrackCollection
     * If we are showing artists, we need to go to AlbumList
     */
    override fun onItemClick(item: ArtistOrIndex) {
        Companion.onItemClick(item, findNavController())
    }

    companion object {
        fun onItemClick(item: ArtistOrIndex, navController: NavController) {
            val bundle = Bundle()

            // Common arguments
            bundle.putString(Constants.INTENT_ID, item.id)
            bundle.putString(Constants.INTENT_NAME, item.name)
            bundle.putString(Constants.INTENT_PARENT_ID, item.id)
            bundle.putBoolean(Constants.INTENT_ARTIST, (item is Artist))

            // Check type
            if (item is Index) {
                navController.navigate(R.id.artistsListToTrackCollection, bundle)
            } else {
                bundle.putString(Constants.INTENT_ALBUM_LIST_TYPE, Constants.ALBUMS_OF_ARTIST)
                bundle.putString(Constants.INTENT_ALBUM_LIST_TITLE, item.name)
                bundle.putInt(Constants.INTENT_ALBUM_LIST_SIZE, 1000)
                bundle.putInt(Constants.INTENT_ALBUM_LIST_OFFSET, 0)
                navController.navigate(R.id.artistsListToAlbumsList, bundle)
            }
        }
    }
}
