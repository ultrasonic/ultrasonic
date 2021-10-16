/*
 * ArtistRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView.SectionedAdapter
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings

/**
 * Creates a Row in a RecyclerView which contains the details of an Artist
 */
class ArtistRowAdapter(
    itemList: List<ArtistOrIndex>,
    onItemClick: (ArtistOrIndex) -> Unit,
    onContextMenuClick: (MenuItem, ArtistOrIndex) -> Boolean,
    private val imageLoader: ImageLoader,
    onMusicFolderUpdate: (String?) -> Unit
) : GenericRowAdapter<ArtistOrIndex>(
    onItemClick,
    onContextMenuClick,
    onMusicFolderUpdate
),
    SectionedAdapter {

    init {
        super.submitList(itemList)
    }

    // Set our layout files
    override val layout = R.layout.artist_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val listPosition = if (selectFolderHeader != null) position - 1 else position
            holder.textView.text = currentList[listPosition].name
            holder.section.text = getSectionForArtist(listPosition)
            holder.layout.setOnClickListener { onItemClick(currentList[listPosition]) }
            holder.layout.setOnLongClickListener { view -> createPopupMenu(view, listPosition) }
            holder.coverArtId = currentList[listPosition].coverArt

            if (Settings.shouldShowArtistPicture) {
                holder.coverArt.visibility = View.VISIBLE
                val key = FileUtil.getArtistArtKey(currentList[listPosition].name, false)
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
    }

    override fun getSectionName(position: Int): String {
        var listPosition = if (selectFolderHeader != null) position - 1 else position

        // Show the first artist's initial in the popup when the list is
        // scrolled up to the "Select Folder" row
        if (listPosition < 0) listPosition = 0

        return getSectionFromName(currentList[listPosition].name ?: " ")
    }

    private fun getSectionForArtist(artistPosition: Int): String {
        if (artistPosition == 0)
            return getSectionFromName(currentList[artistPosition].name ?: " ")

        val previousArtistSection = getSectionFromName(
            currentList[artistPosition - 1].name ?: " "
        )
        val currentArtistSection = getSectionFromName(
            currentList[artistPosition].name ?: " "
        )

        return if (previousArtistSection == currentArtistSection) "" else currentArtistSection
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
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
