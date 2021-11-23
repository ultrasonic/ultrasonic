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
        val diffAdapter = adapter as BaseAdapter<*>

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
            diffAdapter.isSelected(item.longId)
        )

        // Notify the adapter of selection changes
        holder.observableChecked.observe(
            lifecycleOwner,
            { newValue ->
                if (newValue) {
                    diffAdapter.notifySelected(item.longId)
                } else {
                    diffAdapter.notifyUnselected(item.longId)
                }
            }
        )

        // Listen to changes in selection status and update ourselves
        diffAdapter.selectionRevision.observe(
            lifecycleOwner,
            {
                val newStatus = diffAdapter.isSelected(item.longId)

                if (newStatus != holder.check.isChecked) holder.check.isChecked = newStatus
            }
        )

        // Observe download status
        downloadFile.status.observe(
            lifecycleOwner,
            {
                holder.updateStatus(it)
                diffAdapter.notifyChanged()
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
