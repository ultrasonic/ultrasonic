package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle

/**
 * Lists the Bookmarks available on the server
 */
class BookmarksFragment : TrackCollectionFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(this, R.string.button_bar_bookmarks)
    }

    override fun setupButtons(view: View) {
        super.setupButtons(view)

        // Why?
        selectButton?.visibility = View.GONE
        moreButton?.visibility = View.GONE
    }

    override fun getLiveData(args: Bundle?): LiveData<List<MusicDirectory.Entry>> {
        listModel.viewModelScope.launch(handler) {
            refreshListView?.isRefreshing = true
            listModel.getBookmarks()
            refreshListView?.isRefreshing = false
        }
        return listModel.currentList
    }

    override fun enableButtons(selection: List<MusicDirectory.Entry>) {
        super.enableButtons(selection)
    }
}








