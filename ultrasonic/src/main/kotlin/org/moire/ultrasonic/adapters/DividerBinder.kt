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
class DividerBinder : ItemViewBinder<DividerBinder.Divider, DividerBinder.ViewHolder>() {

    // Set our layout files
    val layout = R.layout.row_divider

    override fun onBindViewHolder(holder: ViewHolder, item: Divider) {
        // Set text
        holder.textView.setText(item.stringId)
    }

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    // ViewHolder class
    class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView.findViewById(R.id.text)
    }

    // Class to store our data into
    data class Divider(val stringId: Int) : Identifiable {
        override val id: String
            get() = stringId.toString()
        override val longId: Long
            get() = stringId.toLong()

        override fun compareTo(other: Identifiable): Int = longId.compareTo(other.longId)
    }
}
