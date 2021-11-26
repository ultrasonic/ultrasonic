package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.util.Constants

/**
 * Displays the list of Artists from the media library
 */
class ArtistListFragment : EntryListFragment<ArtistOrIndex>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: ArtistListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout = R.layout.generic_list

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    override val itemClickTarget = R.id.selectArtistToSelectAlbum

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(args: Bundle?): LiveData<List<ArtistOrIndex>> {
        val refresh = args?.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH) ?: false
        return listModel.getItems(refresh, refreshListView!!)
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

    override fun onItemClick(item: ArtistOrIndex) {
        val bundle = Bundle()
        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, item.id)
        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, item.name)
        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, item.id)
        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, (item is Artist))
        bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, Constants.ALBUMS_OF_ARTIST)
        bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, item.name)
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 1000)
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0)
        findNavController().navigate(itemClickTarget, bundle)
    }
}
