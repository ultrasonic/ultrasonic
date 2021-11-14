package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
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
import timber.log.Timber

class TrackViewBinder(
    val checkable: Boolean,
    val draggable: Boolean,
    context: Context,
    val lifecycleOwner: LifecycleOwner
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
        return TrackViewHolder(inflater.inflate(layout, parent, false), adapter as MultiTypeDiffAdapter<Identifiable>)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, item: Identifiable) {

        val downloadFile: DownloadFile?

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
            holder.adapter.isSelected(item.longId)
        )

        // Listen to changes in selection status and update ourselves
        holder.adapter.selectionRevision.observe(lifecycleOwner, {
            val newStatus = holder.adapter.isSelected(item.longId)

            if (newStatus != holder.check.isChecked) holder.check.isChecked = newStatus
        })

        // Observe download status
        downloadFile.status.observe(lifecycleOwner, {
                Timber.w("CAUGHT STATUS CHANGE")
                holder.updateStatus(it)
                holder.adapter.notifyChanged()
            }
        )

        downloadFile.progress.observe(lifecycleOwner, {
                Timber.w("CAUGHT PROGRESS CHANGE")
                holder.updateProgress(it)
            }
        )
    }


}



