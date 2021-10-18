package org.moire.ultrasonic.fragment

import android.app.Application
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.MultiTypeDiffAdapter
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.Util
import java.util.TreeSet

class DownloadsFragment : MultiListFragment<DownloadFile, MultiTypeDiffAdapter<Identifiable>>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: DownloadListModel by viewModels()

    /**
     * The id of the target in the navigation graph where we should go,
     * after the user has clicked on an item
     */
    // FIXME
    override val itemClickTarget: Int = R.id.trackCollectionFragment

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(args: Bundle?): LiveData<List<DownloadFile>> {
        return listModel.getList()
    }

    /**
     * Provide the Adapter for the RecyclerView with a lazy delegate
     */
    override val viewAdapter: MultiTypeDiffAdapter<Identifiable> by lazy {
        val adapter = MultiTypeDiffAdapter<Identifiable>()
        adapter.register(
            TrackViewBinder(
                selectedSet = TreeSet(),
                checkable = false,
                draggable = false,
                context = requireContext()
            )
        )
        adapter
    }

    override fun onContextMenuItemSelected(menuItem: MenuItem, item: DownloadFile): Boolean {
        // Do nothing
        return true
    }

    override fun onItemClick(item: DownloadFile) {
        // Do nothing
    }

    override fun setTitle(title: String?) {
        FragmentTitle.setTitle(this, Util.appContext().getString(R.string.menu_downloads))
    }
}


class DownloadListModel(application: Application) : GenericListModel(application) {
    private val downloader by inject<Downloader>()

    fun getList(): LiveData<List<DownloadFile>> {
        return downloader.observableDownloads
    }
}



