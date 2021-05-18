package org.moire.ultrasonic.fragment

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.util.Constants

/**
 * Displays the list of Artists from the media library
 */
@KoinApiExtension
class ArtistListFragment : GenericListFragment<Artist, ArtistRowAdapter>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: ArtistListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout = R.layout.generic_list

    /**
     * The id of the refresh view
     */
    override val refreshListId = R.id.generic_list_refresh

    /**
     * The id of the RecyclerView
     */
    override val recyclerViewId = R.id.generic_list_recycler

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    override val itemClickTarget = R.id.selectArtistToSelectAlbum

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(args: Bundle?): LiveData<List<Artist>> {
        val refresh = args?.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH) ?: false
        return listModel.getItems(refresh, refreshListView!!)
    }

    /**
     * Provide the Adapter for the RecyclerView with a lazy delegate
     */
    override val viewAdapter: ArtistRowAdapter by lazy {
        ArtistRowAdapter(
            liveDataItems.value ?: listOf(),
            { entry -> onItemClick(entry) },
            { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
            imageLoaderProvider.getImageLoader(),
            onMusicFolderUpdate
        )
    }
}
