package org.moire.ultrasonic.view

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicFolder

/**
 * This little view shows the currently selected Folder (or catalog) on the music server.
 * When clicked it will drop down a list of all available Folders and allow you to
 * select one. The intended usage is to supply a filter to lists of artists, albums, etc
 */
class SelectMusicFolderView(
    private val context: Context,
    view: View,
    private val onUpdate: (String?) -> Unit
) : RecyclerView.ViewHolder(view) {
    private var musicFolders: List<MusicFolder> = mutableListOf()
    private var selectedFolderId: String? = null
    private val folderName: TextView = itemView.findViewById(R.id.select_folder_name)
    private val layout: LinearLayout = itemView.findViewById(R.id.select_folder_header)

    init {
        folderName.text = context.getString(R.string.select_artist_all_folders)
        layout.setOnClickListener { onFolderClick() }
    }

    fun setData(selectedId: String?, folders: List<MusicFolder>) {
        selectedFolderId = selectedId
        musicFolders = folders
        if (selectedFolderId != null) {
            for ((id, name) in musicFolders) {
                if (id == selectedFolderId) {
                    folderName.text = name
                    break
                }
            }
        } else {
            folderName.text = context.getString(R.string.select_artist_all_folders)
        }
    }

    private fun onFolderClick() {
        val popup = PopupMenu(context, layout)

        var menuItem = popup.menu.add(
            MENU_GROUP_MUSIC_FOLDER, -1, 0, R.string.select_artist_all_folders
        )
        if (selectedFolderId == null || selectedFolderId!!.isEmpty()) {
            menuItem.isChecked = true
        }
        musicFolders.forEachIndexed { i, musicFolder ->
            val (id, name) = musicFolder
            menuItem = popup.menu.add(MENU_GROUP_MUSIC_FOLDER, i, i + 1, name)
            if (id == selectedFolderId) {
                menuItem.isChecked = true
            }
        }

        popup.menu.setGroupCheckable(MENU_GROUP_MUSIC_FOLDER, true, true)

        popup.setOnMenuItemClickListener { item -> onFolderMenuItemSelected(item) }
        popup.show()
    }

    private fun onFolderMenuItemSelected(menuItem: MenuItem): Boolean {
        val selectedFolder = if (menuItem.itemId == -1) null else musicFolders[menuItem.itemId]
        val musicFolderName = selectedFolder?.name
            ?: context.getString(R.string.select_artist_all_folders)
        selectedFolderId = selectedFolder?.id

        menuItem.isChecked = true
        folderName.text = musicFolderName
        onUpdate(selectedFolderId)

        return true
    }

    companion object {
        const val MENU_GROUP_MUSIC_FOLDER = 10
    }
}
