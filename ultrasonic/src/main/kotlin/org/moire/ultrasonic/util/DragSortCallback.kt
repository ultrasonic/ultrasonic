package org.moire.ultrasonic.util

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.adapters.BaseAdapter

class DragSortCallback : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {

        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition

        // FIXME: Move it in the data set
        (recyclerView.adapter as BaseAdapter<*>).moveItem(from, to)

        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }
}
