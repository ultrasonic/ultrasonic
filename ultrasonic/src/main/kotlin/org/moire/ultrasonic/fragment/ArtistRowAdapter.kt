/*
 * ArtistRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView.SectionedAdapter
import java.text.Collator
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.util.Util

/**
 * Creates a Row in a RecyclerView which contains the details of an Artist
 */
class ArtistRowAdapter(
    artistList: List<Artist>,
    onItemClick: (Artist) -> Unit,
    onContextMenuClick: (MenuItem, Artist) -> Boolean,
    private val imageLoader: ImageLoader,
    onMusicFolderUpdate: (String?) -> Unit
) : GenericRowAdapter<Artist>(
    onItemClick,
    onContextMenuClick,
    imageLoader,
    onMusicFolderUpdate
),
    SectionedAdapter {

    override var itemList = artistList

    // Set our layout files
    override val layout = R.layout.artist_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    /**
     * Sets the data to be displayed in the RecyclerView
     */
    override fun setData(data: List<Artist>) {
        itemList = data.sortedWith(compareBy(Collator.getInstance()) { t -> t.name })
        super.notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val listPosition = if (selectFolderHeader != null) position - 1 else position
            holder.textView.text = itemList[listPosition].name
            holder.section.text = getSectionForArtist(listPosition)
            holder.layout.setOnClickListener { onItemClick(itemList[listPosition]) }
            holder.layout.setOnLongClickListener { view -> createPopupMenu(view, listPosition) }
            holder.coverArtId = itemList[listPosition].coverArt

            if (Util.getShouldShowArtistPicture()) {
                holder.coverArt.visibility = View.VISIBLE
                imageLoader.loadImage(
                    holder.coverArt,
                    MusicDirectory.Entry().apply { coverArt = holder.coverArtId },
                    false, 0, false, true, R.drawable.ic_contact_picture
                )
            } else {
                holder.coverArt.visibility = View.GONE
            }
        }
    }

    override fun getSectionName(position: Int): String {
        var listPosition = if (selectFolderHeader != null) position - 1 else position

        // Show the first artist's initial in the popup when the list is
        // scrolled up to the "Select Folder" row
        if (listPosition < 0) listPosition = 0

        return getSectionFromName(itemList[listPosition].name ?: " ")
    }

    private fun getSectionForArtist(artistPosition: Int): String {
        if (artistPosition == 0)
            return getSectionFromName(itemList[artistPosition].name ?: " ")

        val previousArtistSection = getSectionFromName(
            itemList[artistPosition - 1].name ?: " "
        )
        val currentArtistSection = getSectionFromName(
            itemList[artistPosition].name ?: " "
        )

        return if (previousArtistSection == currentArtistSection) "" else currentArtistSection
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().toUpperCase()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    /**
     * Creates an instance of our ViewHolder class
     */
    override fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return ViewHolder(view)
    }
}
