package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import java.lang.ref.WeakReference
import java.util.Random
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.AlbumHeader
import org.moire.ultrasonic.util.Util

/**
 * This Binder can bind a list of entries into a Header
 */
class HeaderViewBinder(
    context: Context
) : ItemViewBinder<AlbumHeader, HeaderViewBinder.ViewHolder>(), KoinComponent {

    private val weakContext: WeakReference<Context> = WeakReference(context)
    private val random: Random = Random()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    // Set our layout files
    val layout = R.layout.select_album_header

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverArtView: ImageView = itemView.findViewById(R.id.select_album_art)
        val titleView: TextView = itemView.findViewById(R.id.select_album_title)
        val artistView: TextView = itemView.findViewById(R.id.select_album_artist)
        val durationView: TextView = itemView.findViewById(R.id.select_album_duration)
        val songCountView: TextView = itemView.findViewById(R.id.select_album_song_count)
        val yearView: TextView = itemView.findViewById(R.id.select_album_year)
        val genreView: TextView = itemView.findViewById(R.id.select_album_genre)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: AlbumHeader) {

        val context = weakContext.get() ?: return
        val resources = context.resources

        val artworkSelection = random.nextInt(item.childCount)

        imageLoaderProvider.getImageLoader().loadImage(
            holder.coverArtView, item.entries[artworkSelection], false,
            Util.getAlbumImageSize(context)
        )

        if (item.name != null) {
            holder.titleView.isVisible = true
            holder.titleView.text = item.name
        } else {
            holder.titleView.isVisible = false
        }

        // Don't show a header if all entries are videos
        if (item.isAllVideo) {
            return
        }

        val artist: String = when {
            item.artists.size == 1 -> item.artists.iterator().next()
            item.grandParents.size == 1 -> item.grandParents.iterator().next()
            else -> context.resources.getString(R.string.common_various_artists)
        }
        holder.artistView.text = artist

        val genre: String = if (item.genres.size == 1) {
            item.genres.iterator().next()
        } else {
            context.resources.getString(R.string.common_multiple_genres)
        }

        holder.genreView.text = genre

        val year: String = if (item.years.size == 1) {
            item.years.iterator().next().toString()
        } else {
            resources.getString(R.string.common_multiple_years)
        }

        holder.yearView.text = year

        val songs = resources.getQuantityString(
            R.plurals.select_album_n_songs, item.childCount,
            item.childCount
        )
        holder.songCountView.text = songs

        val duration = Util.formatTotalDuration(item.totalDuration)
        holder.durationView.text = duration
    }
}
