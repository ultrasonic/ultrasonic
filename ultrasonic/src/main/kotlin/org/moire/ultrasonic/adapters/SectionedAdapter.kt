package org.moire.ultrasonic.adapters

import com.drakeet.multitype.MultiTypeAdapter
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import org.moire.ultrasonic.domain.Identifiable

class SectionedAdapter<T : Identifiable> : MultiTypeAdapter(), FastScrollRecyclerView.SectionedAdapter {
    override fun getSectionName(position: Int): String {
//        var listPosition = if (selectFolderHeader != null) position - 1 else position
//
//        // Show the first artist's initial in the popup when the list is
//        // scrolled up to the "Select Folder" row
//        if (listPosition < 0) listPosition = 0
//
//        return getSectionFromName(currentList[listPosition].name ?: " ")
         return "X"
    }
}