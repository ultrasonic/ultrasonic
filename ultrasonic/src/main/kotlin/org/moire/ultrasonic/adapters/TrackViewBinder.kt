package org.moire.ultrasonic.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Checkable
import androidx.recyclerview.selection.SelectionTracker
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.Settings

class TrackViewBinder(
    val selectedSet: MutableSet<Long>,
    val checkable: Boolean,
    val draggable: Boolean,
    context: Context
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
        return TrackViewHolder(inflater.inflate(layout, parent, false), selectedSet)
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
            draggable = draggable
        )
        
        // Observe download status
//        item.status.observe(
//            lifecycleOwner,
//            {
//                holder.updateDownloadStatus(item)
//            }
//        )
//
//        item.progress.observe(
//            lifecycleOwner,
//            {
//                holder.updateDownloadStatus(item)
//            }
//        )
    }


}



