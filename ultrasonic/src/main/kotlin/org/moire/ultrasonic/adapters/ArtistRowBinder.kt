/*
 * ArtistRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings

/**
 * Creates a Row in a RecyclerView which contains the details of an Artist
 * FIXME: On click wrong display...
 */
class ArtistRowBinder(
    val onItemClick: (ArtistOrIndex) -> Unit,
    val onContextMenuClick: (MenuItem, ArtistOrIndex) -> Boolean,
    private val imageLoader: ImageLoader,
) : ItemViewBinder<ArtistOrIndex, ArtistRowBinder.ViewHolder>(), KoinComponent {

    val layout = R.layout.artist_list_item
    val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: ViewHolder, item: ArtistOrIndex) {
        holder.textView.text = item.name
        holder.section.text = getSectionForArtist(item)
        holder.layout.setOnClickListener { onItemClick(item) }
        holder.layout.setOnLongClickListener {
            val popup = Helper.createPopupMenu(holder.itemView)

            popup.setOnMenuItemClickListener { menuItem ->
                onContextMenuClick(menuItem, item)
            }

            true
        }

        holder.coverArtId = item.coverArt

        if (Settings.shouldShowArtistPicture) {
            holder.coverArt.visibility = View.VISIBLE
            val key = FileUtil.getArtistArtKey(item.name, false)
            imageLoader.loadImage(
                view = holder.coverArt,
                id = holder.coverArtId,
                key = key,
                large = false,
                size = 0,
                defaultResourceId = R.drawable.ic_contact_picture
            )
        } else {
            holder.coverArt.visibility = View.GONE
        }
    }

    private fun getSectionForArtist(item: ArtistOrIndex): String {
        val index = adapter.items.indexOf(item)

        if (index == -1) return " "

        if (index == 0) return getSectionFromName(item.name ?: " ")

        val previousItem = adapter.items[index - 1]
        val previousSectionKey: String

        if (previousItem is ArtistOrIndex) {
            previousSectionKey = getSectionFromName(previousItem.name ?: " ")
        } else {
            previousSectionKey = " "
        }

        val currentSectionKey = getSectionFromName(item.name ?: "")

        return if (previousSectionKey == currentSectionKey) "" else currentSectionKey
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    /**
     * Creates an instance of our ViewHolder class
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

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false))
    }
}
