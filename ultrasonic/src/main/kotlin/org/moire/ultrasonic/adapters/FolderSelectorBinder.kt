package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import java.lang.ref.WeakReference
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.RxBus

/**
 * This little view shows the currently selected Folder (or catalog) on the music server.
 * When clicked it will drop down a list of all available Folders and allow you to
 * select one. The intended usage is to supply a filter to lists of artists, albums, etc
 */
class FolderSelectorBinder(context: Context) :
    ItemViewBinder<FolderSelectorBinder.FolderHeader, FolderSelectorBinder.ViewHolder>(),
    KoinComponent {

    private val weakContext: WeakReference<Context> = WeakReference(context)

    // Set our layout files
    val layout = R.layout.list_header_folder

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(layout, parent, false), weakContext)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: FolderHeader) {
        holder.setData(item)
    }

    class ViewHolder(
        view: View,
        private val weakContext: WeakReference<Context>
    ) : RecyclerView.ViewHolder(view) {

        private var data: FolderHeader? = null

        private val selectedFolderId: String?
            get() = data?.selected

        private val musicFolders: List<MusicFolder>
            get() = data?.folders ?: mutableListOf()

        private val folderName: TextView = itemView.findViewById(R.id.select_folder_name)
        private val layout: LinearLayout = itemView.findViewById(R.id.select_folder_header)

        init {
            folderName.text = weakContext.get()!!.getString(R.string.select_artist_all_folders)
            layout.setOnClickListener { onFolderClick() }
        }

        fun setData(item: FolderHeader) {
            data = item
            if (selectedFolderId != null) {
                for ((id, name) in musicFolders) {
                    if (id == selectedFolderId) {
                        folderName.text = name
                        break
                    }
                }
            } else {
                folderName.text = weakContext.get()!!.getString(R.string.select_artist_all_folders)
            }
        }

        private fun onFolderClick() {
            val popup = PopupMenu(weakContext.get()!!, layout)

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
                ?: weakContext.get()!!.getString(R.string.select_artist_all_folders)

            data?.selected = selectedFolder?.id

            menuItem.isChecked = true
            folderName.text = musicFolderName

            RxBus.musicFolderChangedEventPublisher.onNext(selectedFolderId)

            return true
        }

        companion object {
            const val MENU_GROUP_MUSIC_FOLDER = 10
        }
    }

    data class FolderHeader(
        var folders: List<MusicFolder>,
        var selected: String?
    ) : Identifiable {
        override val id: String
            get() = "FOLDERSELECTOR"

        override val longId: Long
            get() = -1L
    }
}
