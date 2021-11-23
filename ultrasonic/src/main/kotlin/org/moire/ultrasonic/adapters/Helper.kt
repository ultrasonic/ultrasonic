package org.moire.ultrasonic.adapters

import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider

object Helper {
    @JvmStatic
    fun createPopupMenu(view: View, contextMenuLayout: Int = R.menu.artist_context_menu): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(contextMenuLayout, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.menu_download)
        downloadMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        popup.show()
        return popup
    }
}
