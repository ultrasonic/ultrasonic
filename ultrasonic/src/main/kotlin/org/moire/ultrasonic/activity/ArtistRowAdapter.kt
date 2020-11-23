/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2020 (C) Jozsef Varga
 */
package org.moire.ultrasonic.activity

import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView.SectionedAdapter
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.ImageLoader
import java.text.Collator

/**
 * Creates a Row in a RecyclerView which contains the details of an Artist
 */
class ArtistRowAdapter(
    private var artistList: List<Artist>,
    private var folderName: String,
    private var shouldShowHeader: Boolean,
    val onArtistClick: (Artist) -> Unit,
    val onContextMenuClick: (MenuItem, Artist) -> Boolean,
    val onFolderClick: (view: View) -> Unit,
    private val imageLoader: ImageLoader
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), SectionedAdapter {

    /**
     * Sets the data to be displayed in the RecyclerView
     */
    fun setData(data: List<Artist>) {
        artistList = data.sortedWith(compareBy(Collator.getInstance()){ t -> t.name })
        notifyDataSetChanged()
    }

    /**
     * Sets the name of the folder to be displayed n the Header (first) row
     */
    fun setFolderName(name: String) {
        folderName = name
        notifyDataSetChanged()
    }

    /**
     * Holds the view properties of an Artist row
     */
    class ArtistViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        var section: TextView = itemView.findViewById(R.id.row_section)
        var textView: TextView = itemView.findViewById(R.id.row_artist_name)
        var layout: RelativeLayout = itemView.findViewById(R.id.row_artist_layout)
        var coverArt: ImageView = itemView.findViewById(R.id.artist_coverart)
        var coverArtId: String? = null
    }

    /**
     * Holds the view properties of the Header row
     */
    class HeaderViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        var folderName: TextView = itemView.findViewById(R.id.select_artist_folder_2)
        var layout: LinearLayout = itemView.findViewById(R.id.select_artist_folder)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.artist_list_item, parent, false)
            return ArtistViewHolder(row)
        }
        val header = LayoutInflater.from(parent.context)
            .inflate(R.layout.select_artist_header, parent, false)
        return HeaderViewHolder(header)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if ((holder is ArtistViewHolder) && (holder.coverArtId != null)) {
            imageLoader.cancel(holder.coverArtId)
        }
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ArtistViewHolder) {
            val listPosition = if (shouldShowHeader) position - 1 else position
            holder.textView.text = artistList[listPosition].name
            holder.section.text = getSectionForArtist(listPosition)
            holder.layout.setOnClickListener { onArtistClick(artistList[listPosition]) }
            holder.layout.setOnLongClickListener { view -> createPopupMenu(view, listPosition) }
            holder.coverArtId = artistList[listPosition].coverArt
            imageLoader.loadImage(
                holder.coverArt,
                MusicDirectory.Entry().apply { coverArt = holder.coverArtId },
                false, 0, false, true
            )
        } else if (holder is HeaderViewHolder) {
            holder.folderName.text = folderName
            holder.layout.setOnClickListener { onFolderClick(holder.layout) }
        }
    }

    override fun getItemCount() = if (shouldShowHeader) artistList.size + 1 else artistList.size

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && shouldShowHeader) TYPE_HEADER else TYPE_ITEM
    }

    override fun getSectionName(position: Int): String {
        val listPosition = if (shouldShowHeader) position - 1 else position
        return getSectionFromName(artistList[listPosition].name ?: " ")
    }

    private fun getSectionForArtist(artistPosition: Int): String {
        if (artistPosition == 0)
            return getSectionFromName(artistList[artistPosition].name ?: " ")

        val previousArtistSection = getSectionFromName(
            artistList[artistPosition - 1].name ?: " "
        )
        val currentArtistSection = getSectionFromName(
            artistList[artistPosition].name ?: " "
        )

        return if (previousArtistSection == currentArtistSection) "" else currentArtistSection
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().toUpperCase()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    private fun createPopupMenu(view: View, position: Int): Boolean {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.select_artist_context, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.artist_menu_download)
        downloadMenuItem?.isVisible = !isOffline(view.context)

        popup.setOnMenuItemClickListener { menuItem ->
            onContextMenuClick(menuItem, artistList[position])
        }
        popup.show()
        return true
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
