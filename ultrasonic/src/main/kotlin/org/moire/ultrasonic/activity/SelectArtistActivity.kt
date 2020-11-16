/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2020 (C) Jozsef Varga
 */
package org.moire.ultrasonic.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util

class SelectArtistActivity : SubsonicTabActivity() {
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()

    private var refreshArtistListView: SwipeRefreshLayout? = null
    private var artistListView: RecyclerView? = null
    private var musicFolders: List<MusicFolder>? = null
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var viewAdapter: ArtistRowAdapter

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_artist)

        refreshArtistListView = findViewById(R.id.select_artist_refresh)
        refreshArtistListView!!.setOnRefreshListener {
            artistListModel.refresh(refreshArtistListView!!)
        }

        val shouldShowHeader = (!isOffline(this) && !Util.getShouldUseId3Tags(this))

        val title = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE)
        if (title == null) {
            setActionBarSubtitle(
                if (isOffline(this)) R.string.music_library_label_offline
                else R.string.music_library_label
            )
        } else {
            actionBarSubtitle = title
        }
        val browseMenuItem = findViewById<View>(R.id.menu_browse)
        menuDrawer.setActiveView(browseMenuItem)
        musicFolders = null

        val refresh = intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false)

        artistListModel.getMusicFolders(refresh, refreshArtistListView!!)
            .observe(
                this,
                Observer { changedFolders ->
                    if (changedFolders != null) {
                        musicFolders = changedFolders
                        viewAdapter.setFolderName(getMusicFolderName(changedFolders))
                    }
                }
            )

        val artists = artistListModel.getArtists(refresh, refreshArtistListView!!)
        artists.observe(
            this, Observer { changedArtists -> viewAdapter.setData(changedArtists) }
        )

        viewManager = LinearLayoutManager(this)
        viewAdapter = ArtistRowAdapter(
            artists.value ?: listOf(),
            getText(R.string.select_artist_all_folders).toString(),
            shouldShowHeader,
            { artist -> onItemClick(artist) },
            { menuItem, artist -> onArtistMenuItemSelected(menuItem, artist) },
            { view -> onFolderClick(view) }
        )

        artistListView = findViewById<RecyclerView>(R.id.select_artist_list).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                menuDrawer.toggleMenu()
                return true
            }
            R.id.main_shuffle -> {
                val intent = Intent(this, DownloadActivity::class.java)
                intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true)
                startActivityForResultWithoutTransition(this, intent)
                return true
            }
        }
        return false
    }

    private fun getMusicFolderName(musicFolders: List<MusicFolder>): String {
        val musicFolderId = activeServerProvider.getActiveServer().musicFolderId
        if (musicFolderId != null && musicFolderId != "") {
            for ((id, name) in musicFolders) {
                if (id == musicFolderId) {
                    return name
                }
            }
        }
        return getText(R.string.select_artist_all_folders).toString()
    }

    private fun onItemClick(artist: Artist) {
        val intent = Intent(this, SelectAlbumActivity::class.java)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, artist.id)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, artist.name)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_PARENT_ID, artist.id)
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ARTIST, true)
        startActivityForResultWithoutTransition(this, intent)
    }

    private fun onFolderClick(view: View) {
        val popup = PopupMenu(this, view)

        val musicFolderId = activeServerProvider.getActiveServer().musicFolderId
        var menuItem = popup.menu.add(
            MENU_GROUP_MUSIC_FOLDER, -1, 0, R.string.select_artist_all_folders
        )
        if (musicFolderId == null || musicFolderId.isEmpty()) {
            menuItem.isChecked = true
        }
        if (musicFolders != null) {
            for (i in musicFolders!!.indices) {
                val (id, name) = musicFolders!![i]
                menuItem = popup.menu.add(MENU_GROUP_MUSIC_FOLDER, i, i + 1, name)
                if (id == musicFolderId) {
                    menuItem.isChecked = true
                }
            }
        }
        popup.menu.setGroupCheckable(MENU_GROUP_MUSIC_FOLDER, true, true)

        popup.setOnMenuItemClickListener { item -> onFolderMenuItemSelected(item) }
        popup.show()
    }

    private fun onArtistMenuItemSelected(menuItem: MenuItem, artist: Artist): Boolean {
        when (menuItem.itemId) {
            R.id.artist_menu_play_now ->
                downloadRecursively(artist.id, false, false, true, false, false, false, false, true)
            R.id.artist_menu_play_next ->
                downloadRecursively(artist.id, false, false, true, true, false, true, false, true)
            R.id.artist_menu_play_last ->
                downloadRecursively(artist.id, false, true, false, false, false, false, false, true)
            R.id.artist_menu_pin ->
                downloadRecursively(artist.id, true, true, false, false, false, false, false, true)
            R.id.artist_menu_unpin ->
                downloadRecursively(artist.id, false, false, false, false, false, false, true, true)
            R.id.artist_menu_download ->
                downloadRecursively(artist.id, false, false, false, false, true, false, false, true)
        }
        return true
    }

    private fun onFolderMenuItemSelected(menuItem: MenuItem): Boolean {
        val selectedFolder = if (menuItem.itemId == -1) null else musicFolders!![menuItem.itemId]
        val musicFolderId = selectedFolder?.id
        val musicFolderName = selectedFolder?.name
            ?: getString(R.string.select_artist_all_folders)
        if (!isOffline(this)) {
            val currentSetting = activeServerProvider.getActiveServer()
            currentSetting.musicFolderId = musicFolderId
            serverSettingsModel.updateItem(currentSetting)
        }
        viewAdapter.setFolderName(musicFolderName)
        artistListModel.refresh(refreshArtistListView!!)
        return true
    }

    companion object {
        private const val MENU_GROUP_MUSIC_FOLDER = 10
    }
}
