package org.moire.ultrasonic.adapters.legacy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.LifecycleOwner
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ImageHelper
import org.moire.ultrasonic.adapters.TrackViewHolder
import org.moire.ultrasonic.service.DownloadFile

/**
 * Legacy bridge to provide Views to a ListView using RecyclerView.ViewHolders
 */
class SongListAdapter(
    ctx: Context,
    entries: List<DownloadFile?>?,
    val lifecycleOwner: LifecycleOwner
) :
    ArrayAdapter<DownloadFile?>(ctx, android.R.layout.simple_list_item_1, entries!!) {

    val layout = R.layout.song_list_item
    private val imageHelper: ImageHelper = ImageHelper(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val downloadFile = getItem(position)!!
        var view = convertView
        val holder: TrackViewHolder

        if (view == null) {
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(layout, parent, false)
        }

        if (view?.tag is TrackViewHolder) {
            holder = view.tag as TrackViewHolder
        } else {
            holder = TrackViewHolder(view!!)
            view.tag = holder
        }

        holder.imageHelper = imageHelper

        holder.setSong(
            file = downloadFile,
            checkable = false,
            draggable = true
        )

        // Observe download status
        downloadFile.status.observe(
            lifecycleOwner,
            {
                holder.updateStatus(it)
            }
        )

        downloadFile.progress.observe(
            lifecycleOwner,
            {
                holder.updateProgress(it)
            }
        )

        return view
    }
}
