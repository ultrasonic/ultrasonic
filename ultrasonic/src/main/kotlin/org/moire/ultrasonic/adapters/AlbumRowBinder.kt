/*
 * AlbumRowBinder.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
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
class AlbumRowBinder(
    val onItemClick: (MusicDirectory.Entry) -> Unit,
    val onContextMenuClick: (MenuItem, MusicDirectory.Entry) -> Boolean,
    private val imageLoader: ImageLoader,
    context: Context,
) : ItemViewBinder<MusicDirectory.Entry, AlbumRowBinder.ViewHolder>(), KoinComponent {

    private val starDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_full)
    private val starHollowDrawable: Drawable =
        Util.getDrawableFromAttribute(context, R.attr.star_hollow)

    // Set our layout files
    val layout = R.layout.album_list_item
    val contextMenuLayout = R.menu.artist_context_menu

    override fun onBindViewHolder(holder: ViewHolder, item: MusicDirectory.Entry) {
        holder.album.text = item.title
        holder.artist.text = item.artist
        holder.details.setOnClickListener { onItemClick(item) }
        holder.details.setOnLongClickListener {
            val popup = Helper.createPopupMenu(holder.itemView)

            popup.setOnMenuItemClickListener { menuItem ->
                    onContextMenuClick(menuItem, item)
            }

            true
        }
        holder.coverArtId = item.coverArt
        holder.star.setImageDrawable(if (item.starred) starDrawable else starHollowDrawable)
        holder.star.setOnClickListener { onStarClick(item, holder.star) }

        imageLoader.loadImage(
            holder.coverArt, item,
            false, 0, R.drawable.unknown_album
        )
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

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false))
    }
}

