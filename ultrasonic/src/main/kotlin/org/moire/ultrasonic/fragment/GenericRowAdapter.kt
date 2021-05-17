/*
 * GenericRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.view.SelectMusicFolderView

/*
* An abstract Adapter, which can be extended to display a List of <T> in a RecyclerView
*/
abstract class GenericRowAdapter<T>(
    val onItemClick: (T) -> Unit,
    val onContextMenuClick: (MenuItem, T) -> Boolean,
    private val imageLoader: ImageLoader,
    private val onMusicFolderUpdate: (String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    open var itemList: List<T> = listOf()
    protected abstract val layout: Int
    protected abstract val contextMenuLayout: Int

    var folderHeaderEnabled: Boolean = true
    var selectFolderHeader: SelectMusicFolderView? = null
    var musicFolders: List<MusicFolder> = listOf()
    var selectedFolder: String? = null

    /**
     * Sets the data to be displayed in the RecyclerView
     */
    open fun setData(data: List<T>) {
        itemList = data
        notifyDataSetChanged()
    }

    /**
     * Sets the content and state of the music folder selector row
     */
    fun setFolderList(changedFolders: List<MusicFolder>, selectedId: String?) {
        musicFolders = changedFolders
        selectedFolder = selectedId

        selectFolderHeader?.setData(
            selectedFolder,
            musicFolders
        )

        notifyDataSetChanged()
    }

    open fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return ViewHolder(view)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val row = LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
            return newViewHolder(row)
        } else {
            val row = LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.select_folder_header, parent, false
                )
            selectFolderHeader = SelectMusicFolderView(parent.context, row, onMusicFolderUpdate)

            if (musicFolders.isNotEmpty()) {
                selectFolderHeader?.setData(
                    selectedFolder,
                    musicFolders
                )
            }

            return selectFolderHeader!!
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if ((holder is ViewHolder) && (holder.coverArtId != null)) {
            imageLoader.cancel(holder.coverArtId)
        }
        super.onViewRecycled(holder)
    }

    abstract override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)

    override fun getItemCount(): Int {
        if (selectFolderHeader != null)
            return itemList.size + 1
        else
            return itemList.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && folderHeaderEnabled) TYPE_HEADER else TYPE_ITEM
    }

    internal fun createPopupMenu(view: View, position: Int): Boolean {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(contextMenuLayout, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.menu_download)
        downloadMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        popup.setOnMenuItemClickListener { menuItem ->
            onContextMenuClick(menuItem, itemList[position])
        }
        popup.show()
        return true
    }

    /**
     * Holds the view properties of an Item row
     */
    class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        var section: TextView = itemView.findViewById(R.id.row_section)
        var textView: TextView = itemView.findViewById(R.id.row_artist_name)
        var layout: RelativeLayout = itemView.findViewById(R.id.row_artist_layout)
        var coverArt: ImageView = itemView.findViewById(R.id.artist_coverart)
        var coverArtId: String? = null
    }

    companion object {
        internal const val TYPE_HEADER = 0
        internal const val TYPE_ITEM = 1
    }
}
