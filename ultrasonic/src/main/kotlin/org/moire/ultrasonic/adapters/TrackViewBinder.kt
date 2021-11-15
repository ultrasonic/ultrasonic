package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.Downloader

class TrackViewBinder(
    val checkable: Boolean,
    val draggable: Boolean,
    context: Context,
    val lifecycleOwner: LifecycleOwner,
    private val onClickCallback: ((View, DownloadFile?) -> Unit)? = null
) : ItemViewBinder<Identifiable, TrackViewHolder>(), KoinComponent {

//    //
//    onItemClick: (MusicDirectory.Entry) -> Unit,
//    onContextMenuClick: (MenuItem, MusicDirectory.Entry) -> Boolean,
//    onMusicFolderUpdate: (String?) -> Unit,
//    context: Context,
//    val lifecycleOwner: LifecycleOwner,
//    init {
//        super.submitList(itemList)
//    }

    // Set our layout files
    val layout = R.layout.song_list_item
    val contextMenuLayout = R.menu.artist_context_menu

    private val downloader: Downloader by inject()
    private val imageHelper: ImageHelper = ImageHelper(context)

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): TrackViewHolder {
        return TrackViewHolder(inflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: TrackViewHolder, item: Identifiable) {

        val downloadFile: DownloadFile?
        val _adapter = adapter as MultiTypeDiffAdapter<*>

        when (item) {
            is MusicDirectory.Entry -> {
                downloadFile = downloader.getDownloadFileForSong(item)
            }
            is DownloadFile -> {
                downloadFile = item
            }
            else -> {
                return
            }
        }

        holder.imageHelper = imageHelper

        holder.setSong(
            file = downloadFile,
            checkable = checkable,
            draggable = draggable,
            _adapter.isSelected(item.longId)
        )

        // Notify the adapter of selection changes
        holder.observableChecked.observe(
            lifecycleOwner,
            { newValue ->
                if (newValue) {
                    _adapter.notifySelected(item.longId)
                } else {
                    _adapter.notifyUnselected(item.longId)
                }
            }
        )

        // Listen to changes in selection status and update ourselves
        _adapter.selectionRevision.observe(
            lifecycleOwner,
            {
                val newStatus = _adapter.isSelected(item.longId)

                if (newStatus != holder.check.isChecked) holder.check.isChecked = newStatus
            }
        )

        // Observe download status
        downloadFile.status.observe(
            lifecycleOwner,
            {
                holder.updateStatus(it)
                _adapter.notifyChanged()
            }
        )

        downloadFile.progress.observe(
            lifecycleOwner,
            {
                holder.updateProgress(it)
            }
        )

        holder.itemClickListener = onClickCallback
    }
}
