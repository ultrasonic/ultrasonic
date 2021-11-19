package org.moire.ultrasonic.fragment

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.DownloadStatus
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SongView

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
    context: Context,
    val lifecycleOwner: LifecycleOwner
) : GenericRowAdapter<DownloadFile>(
    onItemClick,
    onContextMenuClick,
    onMusicFolderUpdate
) {

    init {
        super.submitList(itemList)
    }

    private val starDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_full)
    private val starHollowDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_hollow)

    // Set our layout files
    override val layout = R.layout.song_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val downloadFile = currentList[position]
            val entry = downloadFile.song
            holder.title.text = entry.title
            holder.artist.text = entry.artist
            holder.star.setImageDrawable(if (entry.starred) starDrawable else starHollowDrawable)

            // Observe download status
            downloadFile.status.observe(
                lifecycleOwner,
                {
                    updateDownloadStatus(downloadFile, holder)
                }
            )

            downloadFile.progress.observe(
                lifecycleOwner,
                {
                    updateDownloadStatus(downloadFile, holder)
                }
            )
        }
    }

    private fun updateDownloadStatus(
        downloadFile: DownloadFile,
        holder: ViewHolder
    ) {

        var image: Drawable? = null

        when (downloadFile.status.value) {
            DownloadStatus.DONE -> {
                image = if (downloadFile.isSaved) SongView.pinImage else SongView.downloadedImage
                holder.status.text = null
            }
            DownloadStatus.DOWNLOADING -> {
                holder.status.text = Util.formatPercentage(downloadFile.progress.value!!)
                image = SongView.downloadingImage
            }
            else -> {
                holder.status.text = null
            }
        }

        // TODO: Migrate the image animation stuff from SongView into this class
        //
        //        if (image != null) {
        //            holder.status.setCompoundDrawablesWithIntrinsicBounds(
        //                image, null, image, null
        //            )
        //        }
        //
        //        if (image === SongView.downloadingImage) {
        //            val frameAnimation = image as AnimationDrawable
        //
        //            frameAnimation.setVisible(true, true)
        //            frameAnimation.start()
        //        }
    }

    /**
     * Holds the view properties of an Item row
     */
    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {
        var check: CheckedTextView = view.findViewById(R.id.song_check)
        var rating: LinearLayout = view.findViewById(R.id.song_rating)
        var fiveStar1: ImageView = view.findViewById(R.id.song_five_star_1)
        var fiveStar2: ImageView = view.findViewById(R.id.song_five_star_2)
        var fiveStar3: ImageView = view.findViewById(R.id.song_five_star_3)
        var fiveStar4: ImageView = view.findViewById(R.id.song_five_star_4)
        var fiveStar5: ImageView = view.findViewById(R.id.song_five_star_5)
        var star: ImageView = view.findViewById(R.id.song_star)
        var drag: ImageView = view.findViewById(R.id.song_drag)
        var track: TextView = view.findViewById(R.id.song_track)
        var title: TextView = view.findViewById(R.id.song_title)
        var artist: TextView = view.findViewById(R.id.song_artist)
        var duration: TextView = view.findViewById(R.id.song_duration)
        var status: TextView = view.findViewById(R.id.song_status)

        init {
            drag.isVisible = false
            star.isVisible = false
            fiveStar1.isVisible = false
            fiveStar2.isVisible = false
            fiveStar3.isVisible = false
            fiveStar4.isVisible = false
            fiveStar5.isVisible = false
            check.isVisible = false
        }
    }

    /**
     * Creates an instance of our ViewHolder class
     */
    override fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return ViewHolder(view)
    }
}

class DownloadListModel(application: Application) : GenericListModel(application) {
    private val downloader by inject<Downloader>()

    fun getList(): LiveData<List<DownloadFile>> {
        return downloader.observableDownloads
    }
}
