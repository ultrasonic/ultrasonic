package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable

/**
 * Creates a row in a RecyclerView which can be used as a divide between different sections
 */
class MoreButtonBinder : ItemViewBinder<MoreButtonBinder.MoreButton, RecyclerView.ViewHolder>() {

    // Set our layout files
    val layout = R.layout.list_item_more_button

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, item: MoreButton) {
        holder.itemView.setOnClickListener {
            item.onClick()
        }
    }

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): RecyclerView.ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    // ViewHolder class
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    // Class to store our data into
    data class MoreButton(
        val stringId: Int,
        val onClick: (() -> Unit)
        ): Identifiable {

        override val id: String
            get() = stringId.toString()
        override val longId: Long
            get() = stringId.toLong()

        override fun compareTo(other: Identifiable): Int = longId.compareTo(other.longId)
    }

}
