/*
 * AlbumRowAdapter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.lang.Exception
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings.shouldUseId3Tags
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Creates a Row in a RecyclerView which contains the details of an Album
 */
class AlbumRowAdapter(
    itemList: List<MusicDirectory.Entry>,
    onItemClick: (MusicDirectory.Entry) -> Unit,
    onContextMenuClick: (MenuItem, MusicDirectory.Entry) -> Boolean,
    private val imageLoader: ImageLoader,
    onMusicFolderUpdate: (String?) -> Unit,
    context: Context,
) : GenericRowAdapter<MusicDirectory.Entry>(
    onItemClick,
    onContextMenuClick,
    onMusicFolderUpdate
) {

    init {
        super.submitList(itemList)
    }

    private val starDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_full)
    private val starHollowDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_hollow)

    // Set our layout files
    override val layout = R.layout.album_list_item
    override val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val listPosition = if (selectFolderHeader != null) position - 1 else position
            val entry = currentList[listPosition]
            holder.album.text = entry.title
            holder.artist.text = entry.artist
            holder.details.setOnClickListener { onItemClick(entry) }
            holder.details.setOnLongClickListener { view -> createPopupMenu(view, listPosition) }
            holder.coverArtId = entry.coverArt
            holder.star.setImageDrawable(if (entry.starred) starDrawable else starHollowDrawable)
            holder.star.setOnClickListener { onStarClick(entry, holder.star) }

            imageLoader.loadImage(
                holder.coverArt, entry,
                false, 0, R.drawable.unknown_album
            )
        }
    }

    override fun getItemCount(): Int {
        if (selectFolderHeader != null)
            return currentList.size + 1
        else
            return currentList.size
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
        var star: ImageView = view.findViewById(R.id.album_star)
        var coverArtId: String? = null
    }

    /**
     * Creates an instance of our ViewHolder class
     */
    override fun newViewHolder(view: View): RecyclerView.ViewHolder {
        return ViewHolder(view)
    }

    /**
     * Handles the star / unstar action for an album
     */
    private fun onStarClick(entry: MusicDirectory.Entry, star: ImageView) {
        entry.starred = !entry.starred
        star.setImageDrawable(if (entry.starred) starDrawable else starHollowDrawable)
        val musicService = getMusicService()
        Thread {
            val useId3 = shouldUseId3Tags
            try {
                if (entry.starred) {
                    musicService.star(
                        if (!useId3) entry.id else null,
                        if (useId3) entry.id else null,
                        null
                    )
                } else {
                    musicService.unstar(
                        if (!useId3) entry.id else null,
                        if (useId3) entry.id else null,
                        null
                    )
                }
            } catch (all: Exception) {
                Timber.e(all)
            }
        }.start()
    }
}
