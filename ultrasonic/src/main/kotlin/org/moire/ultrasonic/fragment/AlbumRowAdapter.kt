/*
 * AlbumRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.ImageLoader

/**
 * Creates a Row in a RecyclerView which contains the details of an Album
 */
class AlbumRowAdapter(
    albumList: List<MusicDirectory.Entry>,
    onItemClick: (MusicDirectory.Entry) -> Unit,
    onContextMenuClick: (MenuItem, MusicDirectory.Entry) -> Boolean,
    private val imageLoader: ImageLoader,
    onMusicFolderUpdate: (String?) -> Unit
) : GenericRowAdapter<MusicDirectory.Entry>(
    onItemClick,
    onContextMenuClick,
    imageLoader,
    onMusicFolderUpdate
) {

    override var itemList = albumList

    // Set our layout files
    override val layout = R.layout.album_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    // Sets the data to be displayed in the RecyclerView
    override fun setData(data: List<MusicDirectory.Entry>) {
        itemList = data
        super.notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val listPosition = if (selectFolderHeader != null) position - 1 else position
            val entry = itemList[listPosition]
            holder.album.text = entry.title
            holder.artist.text = entry.artist
            holder.details.setOnClickListener { onItemClick(entry) }
            holder.details.setOnLongClickListener { view -> createPopupMenu(view, listPosition) }
            holder.coverArtId = entry.coverArt

            imageLoader.loadImage(
                holder.coverArt,
                MusicDirectory.Entry().apply { coverArt = holder.coverArtId },
                false, 0, false, true, R.drawable.unknown_album
            )
        }
    }

    override fun getItemCount(): Int {
        if (selectFolderHeader != null)
            return itemList.size + 1
        else
            return itemList.size
    }

    /**
     * Holds the view properties of an Item row
     */
    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {
        var album: TextView = view.findViewById(R.id.album_title)
        var artist: TextView = view.findViewById(R.id.album_artist)
        var details: LinearLayout = view.findViewById(R.id.row_album_details)
        var coverArt: ImageView = view.findViewById(R.id.album_coverart)
        var coverArtId: String? = null
    }

    /**
     * Creates an instance of our ViewHolder class
     */
    override fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return ViewHolder(view)
    }
}
