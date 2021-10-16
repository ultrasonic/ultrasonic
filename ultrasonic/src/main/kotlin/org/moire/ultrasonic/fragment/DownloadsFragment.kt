package org.moire.ultrasonic.fragment

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.GenericRowAdapter
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SongViewHolder

class DownloadsFragment : GenericListFragment<DownloadFile, DownloadRowAdapter>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: DownloadListModel by viewModels()

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
    override val viewAdapter: DownloadRowAdapter by lazy {
        DownloadRowAdapter(
            liveDataItems.value ?: listOf(),
            { entry -> onItemClick(entry) },
            { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) },
            onMusicFolderUpdate,
            requireContext(),
            viewLifecycleOwner
        )
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

class DownloadRowAdapter(
    itemList: List<DownloadFile>,
    onItemClick: (DownloadFile) -> Unit,
    onContextMenuClick: (MenuItem, DownloadFile) -> Boolean,
    onMusicFolderUpdate: (String?) -> Unit,
    val context: Context,
    val lifecycleOwner: LifecycleOwner
) : GenericRowAdapter<DownloadFile>(
    onItemClick,
    onContextMenuClick,
    onMusicFolderUpdate
) {

    init {
        super.submitList(itemList)
    }


    // Set our layout files
    override val layout = R.layout.song_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SongViewHolder) {
            val downloadFile = currentList[position]

            holder.setSong(downloadFile, checkable = false, draggable = false)

            // Observe download status
            downloadFile.status.observe(
                lifecycleOwner,
                {
                    holder.updateDownloadStatus(downloadFile)
                }
            )

            downloadFile.progress.observe(
                lifecycleOwner,
                {
                    holder.updateDownloadStatus(downloadFile)
                }
            )
        }
    }




    /**
     * Creates an instance of our ViewHolder class
     */
    override fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return SongViewHolder(view, context)
    }


}

class DownloadListModel(application: Application) : GenericListModel(application) {
    private val downloader by inject<Downloader>()

    fun getList(): LiveData<List<DownloadFile>> {
        return downloader.observableDownloads
    }
}



