/*
 * ArtistRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.view.SelectMusicFolderView

/**
 * Creates a Row in a RecyclerView which contains the details of an Artist
 */
class AlbumRowAdapter(
    albumList: List<MusicDirectory.Entry>,
    private var selectFolderHeader: SelectMusicFolderView?,
    onItemClick: (MusicDirectory.Entry) -> Unit,
    onContextMenuClick: (MenuItem, MusicDirectory.Entry) -> Boolean,
    private val imageLoader: ImageLoader
) : GenericRowAdapter<MusicDirectory.Entry>(
    selectFolderHeader,
    onItemClick,
    onContextMenuClick,
    imageLoader
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

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val row = LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
            return AlbumViewHolder(row)
        }
        return selectFolderHeader!!
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AlbumViewHolder) {
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
                false, 0, false, true, R.drawable.ic_contact_picture
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
    class AlbumViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        var album: TextView = itemView.findViewById(R.id.album_title)
        var artist: TextView = itemView.findViewById(R.id.album_artist)
        var details: LinearLayout = itemView.findViewById(R.id.row_album_details)
        var coverArt: ImageView = itemView.findViewById(R.id.album_coverart)
        var coverArtId: String? = null
    }
}
