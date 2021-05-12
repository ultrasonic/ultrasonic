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
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.view.SelectMusicFolderView

/*
* An abstract Adapter, which can be extended to display a List of <T> in a RecyclerView
*/
abstract class GenericRowAdapter<T>(
    private var selectFolderHeader: SelectMusicFolderView?,
    val onItemClick: (T) -> Unit,
    val onContextMenuClick: (MenuItem, T) -> Boolean,
    private val imageLoader: ImageLoader
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    open var itemList: List<T> = listOf()
    protected abstract val layout: Int
    protected abstract val contextMenuLayout: Int

    /**
     * Sets the data to be displayed in the RecyclerView
     */
    open fun setData(data: List<T>) {
        itemList = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val row = LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
            return ItemViewHolder(row)
        }
        return selectFolderHeader!!
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if ((holder is ItemViewHolder) && (holder.coverArtId != null)) {
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
        return if (position == 0 && selectFolderHeader != null) TYPE_HEADER else TYPE_ITEM
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
    class ItemViewHolder(
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
