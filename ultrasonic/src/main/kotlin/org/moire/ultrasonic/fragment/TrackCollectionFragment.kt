/*
 * TrackCollectionFragment.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.HeaderViewBinder
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.AlbumHeader
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Displays a group of tracks, eg. the songs of an album, of a playlist etc.
 * FIXME: Mixed lists are not handled correctly
 */
@Suppress("TooManyFunctions")
open class TrackCollectionFragment : MultiListFragment<MusicDirectory.Entry>() {

    private var albumButtons: View? = null
    internal var selectButton: ImageView? = null
    internal var playNowButton: ImageView? = null
    private var playNextButton: ImageView? = null
    private var playLastButton: ImageView? = null
    internal var pinButton: ImageView? = null
    private var unpinButton: ImageView? = null
    private var downloadButton: ImageView? = null
    private var deleteButton: ImageView? = null
    private var moreButton: ImageView? = null
    private var playAllButtonVisible = false
    private var shareButtonVisible = false
    private var playAllButton: MenuItem? = null
    private var shareButton: MenuItem? = null

    internal val mediaPlayerController: MediaPlayerController by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val shareHandler: ShareHandler by inject()
    internal var cancellationToken: CancellationToken? = null

    override val listModel: TrackCollectionModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_track

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()

        albumButtons = view.findViewById(R.id.menu_album)

        // Setup refresh handler
        refreshListView = view.findViewById(refreshListId)
        refreshListView?.setOnRefreshListener {
            refreshData(true)
        }

        // TODO: remove special casing for songsForGenre
        listModel.songsForGenre.observe(viewLifecycleOwner, songsForGenreObserver)

        setupButtons(view)

        registerForContextMenu(listView!!)
        setHasOptionsMenu(true)

        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        viewAdapter.register(
            HeaderViewBinder(
                context = requireContext()
            )
        )

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = { onItemClick(it.song) },
                onContextMenuClick = { menu, id -> onContextMenuItemSelected(menu, id.song) },
                checkable = true,
                draggable = false,
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner
            )
        )

        enableButtons()

        // Update the buttons when the selection has changed
        viewAdapter.selectionRevision.observe(
            viewLifecycleOwner,
            {
                enableButtons()
            }
        )
    }

    internal open fun setupButtons(view: View) {
        selectButton = view.findViewById(R.id.select_album_select)
        playNowButton = view.findViewById(R.id.select_album_play_now)
        playNextButton = view.findViewById(R.id.select_album_play_next)
        playLastButton = view.findViewById(R.id.select_album_play_last)
        pinButton = view.findViewById(R.id.select_album_pin)
        unpinButton = view.findViewById(R.id.select_album_unpin)
        downloadButton = view.findViewById(R.id.select_album_download)
        deleteButton = view.findViewById(R.id.select_album_delete)
        moreButton = view.findViewById(R.id.select_album_more)

        selectButton?.setOnClickListener {
            selectAllOrNone()
        }

        playNowButton?.setOnClickListener {
            playNow(false)
        }

        playNextButton?.setOnClickListener {
            downloadHandler.download(
                this@TrackCollectionFragment, append = true,
                save = false, autoPlay = false, playNext = true, shuffle = false,
                songs = getSelectedSongs()
            )
        }

        playLastButton!!.setOnClickListener {
            playNow(true)
        }

        pinButton?.setOnClickListener {
            downloadBackground(true)
        }

        unpinButton?.setOnClickListener {
            unpin()
        }

        downloadButton?.setOnClickListener {
            downloadBackground(false)
        }

        deleteButton?.setOnClickListener {
            delete()
        }
    }

    val handler = CoroutineExceptionHandler { _, exception ->
        Handler(Looper.getMainLooper()).post {
            CommunicationError.handleError(exception, context)
        }
        refreshListView?.isRefreshing = false
    }

    private fun refreshData(refresh: Boolean = false) {
        val args = getArgumentsClone()
        args.putBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, refresh)
        getLiveData(args)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        playAllButton = menu.findItem(R.id.select_album_play_all)

        if (playAllButton != null) {
            playAllButton!!.isVisible = playAllButtonVisible
        }

        shareButton = menu.findItem(R.id.menu_item_share)

        if (shareButton != null) {
            shareButton!!.isVisible = shareButtonVisible
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.select_album, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.select_album_play_all) {
            playAll()
            return true
        } else if (itemId == R.id.menu_item_share) {
            shareHandler.createShare(
                this, getSelectedSongs(),
                refreshListView, cancellationToken!!
            )
            return true
        }

        return false
    }

    override fun onDestroyView() {
        cancellationToken!!.cancel()
        super.onDestroyView()
    }

    private fun playNow(append: Boolean) {
        val selectedSongs = getSelectedSongs()

        if (selectedSongs.isNotEmpty()) {
            downloadHandler.download(
                this, append, false, !append, playNext = false,
                shuffle = false, songs = selectedSongs
            )
        } else {
            playAll(false, append)
        }
    }

    /**
     * Get the size of the underlying list
     */
    private val childCount: Int
        get() {
            val count = viewAdapter.getCurrentList().count()
            if (listModel.showHeader) {
                return count - 1
            } else {
                return count
            }
        }

    private fun playAll(shuffle: Boolean = false, append: Boolean = false) {
        var hasSubFolders = false

        for (item in viewAdapter.getCurrentList()) {
            if (item is MusicDirectory.Entry && item.isDirectory) {
                hasSubFolders = true
                break
            }
        }

        val isArtist = arguments?.getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false) ?: false

        // FIXME WHICH id if no arguments?
        val id = arguments?.getString(Constants.INTENT_EXTRA_NAME_ID)

        if (hasSubFolders && id != null) {
            downloadHandler.downloadRecursively(
                fragment = this,
                id = id,
                save = false,
                append = append,
                autoPlay = !append,
                shuffle = shuffle,
                background = false,
                playNext = false,
                unpin = false,
                isArtist = isArtist
            )
        } else {
            downloadHandler.download(
                fragment = this,
                append = append,
                save = false,
                autoPlay = !append,
                playNext = false,
                shuffle = shuffle,
                songs = getAllSongs()
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAllSongs(): List<MusicDirectory.Entry> {
        return viewAdapter.getCurrentList().filter {
            it is MusicDirectory.Entry && !it.isDirectory
        } as List<MusicDirectory.Entry>
    }

    internal fun selectAllOrNone() {
        val someUnselected = viewAdapter.selectedSet.size < childCount

        selectAll(someUnselected, true)
    }

    internal fun selectAll(selected: Boolean, toast: Boolean) {
        var selectedCount = viewAdapter.selectedSet.size * -1

        selectedCount += viewAdapter.setSelectionStatusOfAll(selected)

        // Display toast: N tracks selected
        if (toast) {
            val toastResId = R.string.select_album_n_selected
            Util.toast(activity, getString(toastResId, selectedCount.coerceAtLeast(0)))
        }
    }

    internal open fun enableButtons(selection: List<MusicDirectory.Entry> = getSelectedSongs()) {
        val enabled = selection.isNotEmpty()
        var unpinEnabled = false
        var deleteEnabled = false
        val multipleSelection = viewAdapter.hasMultipleSelection()

        var pinnedCount = 0

        for (song in selection) {
            val downloadFile = mediaPlayerController.getDownloadFileForSong(song)
            if (downloadFile.isWorkDone) {
                deleteEnabled = true
            }
            if (downloadFile.isSaved) {
                pinnedCount++
                unpinEnabled = true
            }
        }

        playNowButton?.isVisible = enabled
        playNextButton?.isVisible = enabled && multipleSelection
        playLastButton?.isVisible = enabled && multipleSelection
        pinButton?.isVisible = (enabled && !isOffline() && selection.size > pinnedCount)
        unpinButton?.isVisible = (enabled && unpinEnabled)
        downloadButton?.isVisible = (enabled && !deleteEnabled && !isOffline())
        deleteButton?.isVisible = (enabled && deleteEnabled)
    }

    internal fun downloadBackground(save: Boolean) {
        var songs = getSelectedSongs()

        if (songs.isEmpty()) {
            songs = getAllSongs()
        }

        downloadBackground(save, songs)
    }

    private fun downloadBackground(save: Boolean, songs: List<MusicDirectory.Entry?>) {
        val onValid = Runnable {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.downloadBackground(songs, save)

            if (save) {
                Util.toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_pinned, songs.size, songs.size
                    )
                )
            } else {
                Util.toast(
                    context,
                    resources.getQuantityString(
                        R.plurals.select_album_n_songs_downloaded, songs.size, songs.size
                    )
                )
            }
        }
        onValid.run()
    }

    internal fun delete() {
        val songs = getSelectedSongs()

        Util.toast(
            context,
            resources.getQuantityString(
                R.plurals.select_album_n_songs_deleted, songs.size, songs.size
            )
        )

        mediaPlayerController.delete(songs)
    }

    internal fun unpin() {
        val songs = getSelectedSongs()
        Util.toast(
            context,
            resources.getQuantityString(
                R.plurals.select_album_n_songs_unpinned, songs.size, songs.size
            )
        )
        mediaPlayerController.unpin(songs)
    }

    private val songsForGenreObserver = Observer<MusicDirectory> { musicDirectory ->

        // Hide more button when results are less than album list size
        if (musicDirectory.size < requireArguments().getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
        )
        ) {
            moreButton!!.visibility = View.GONE
        } else {
            moreButton!!.visibility = View.VISIBLE
        }

        moreButton!!.setOnClickListener {
            val theGenre = requireArguments().getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME)
            val size = requireArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0)
            val theOffset = requireArguments().getInt(
                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
            ) + size
            val bundle = Bundle()
            bundle.putString(Constants.INTENT_EXTRA_NAME_GENRE_NAME, theGenre)
            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size)
            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, theOffset)

            Navigation.findNavController(requireView())
                .navigate(R.id.trackCollectionFragment, bundle)
        }
    }

    override val defaultObserver: (List<MusicDirectory.Entry>) -> Unit = {

        val entryList: MutableList<MusicDirectory.Entry> = it.toMutableList()

        if (listModel.currentListIsSortable && Settings.shouldSortByDisc) {
            Collections.sort(entryList, EntryByDiscAndTrackComparator())
        }

        var allVideos = true
        var songCount = 0

        for (entry in entryList) {
            if (!entry.isVideo) {
                allVideos = false
            }
            if (!entry.isDirectory) {
                songCount++
            }
        }

        val listSize = arguments?.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0) ?: 0

        // Hide select button for video lists and singular selection lists
        selectButton!!.isVisible = (!allVideos && viewAdapter.hasMultipleSelection())

        if (songCount > 0) {
            if (listSize == 0 || songCount < listSize) {
                moreButton!!.visibility = View.GONE
            } else {
                moreButton!!.visibility = View.VISIBLE
                if (arguments?.getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0) ?: 0 > 0) {
                    moreButton!!.setOnClickListener {
                        val offset = requireArguments().getInt(
                            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
                        ) + listSize
                        val bundle = Bundle()
                        bundle.putInt(Constants.INTENT_EXTRA_NAME_RANDOM, 1)
                        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, listSize)
                        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset)
                        Navigation.findNavController(requireView()).navigate(
                            R.id.trackCollectionFragment, bundle
                        )
                    }
                }
            }
        }

        // Show a text if we have no entries
        emptyView.isVisible = entryList.isEmpty()

        enableButtons()

        val isAlbumList = arguments?.containsKey(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE
        ) ?: false

        playAllButtonVisible = !(isAlbumList || entryList.isEmpty()) && !allVideos
        shareButtonVisible = !isOffline() && songCount > 0

        playAllButton?.isVisible = playAllButtonVisible
        shareButton?.isVisible = shareButtonVisible

        if (songCount > 0 && listModel.showHeader) {
            val intentAlbumName = arguments?.getString(Constants.INTENT_EXTRA_NAME_NAME, "")
            val albumHeader = AlbumHeader(it, intentAlbumName)
            val mixedList: MutableList<Identifiable> = mutableListOf(albumHeader)
            mixedList.addAll(entryList)
            viewAdapter.submitList(mixedList)
        } else {
            viewAdapter.submitList(entryList)
        }

        val playAll = arguments?.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false) ?: false

        if (playAll && songCount > 0) {
            playAll(
                arguments?.getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false) ?: false,
                false
            )
        }

        listModel.currentListIsSortable = true
    }

    internal fun getSelectedSongs(): List<MusicDirectory.Entry> {
        // Walk through selected set and get the Entries based on the saved ids.
        return viewAdapter.getCurrentList().mapNotNull {
            if (it is MusicDirectory.Entry && viewAdapter.isSelected(it.longId))
                it
            else
                null
        }
    }

    override fun setTitle(title: String?) {
        setTitle(this@TrackCollectionFragment, title)
    }

    fun setTitle(id: Int) {
        setTitle(this@TrackCollectionFragment, id)
    }

    @Suppress("LongMethod")
    override fun getLiveData(args: Bundle?): LiveData<List<MusicDirectory.Entry>> {
        if (args == null) return listModel.currentList
        val id = args.getString(Constants.INTENT_EXTRA_NAME_ID)
        val isAlbum = args.getBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false)
        val name = args.getString(Constants.INTENT_EXTRA_NAME_NAME)
        val parentId = args.getString(Constants.INTENT_EXTRA_NAME_PARENT_ID)
        val playlistId = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID)
        val podcastChannelId = args.getString(
            Constants.INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID
        )
        val playlistName = args.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME)
        val shareId = args.getString(Constants.INTENT_EXTRA_NAME_SHARE_ID)
        val shareName = args.getString(Constants.INTENT_EXTRA_NAME_SHARE_NAME)
        val genreName = args.getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME)

        val getStarredTracks = args.getInt(Constants.INTENT_EXTRA_NAME_STARRED, 0)
        val getVideos = args.getInt(Constants.INTENT_EXTRA_NAME_VIDEOS, 0)
        val getRandomTracks = args.getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0)
        val albumListSize = args.getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
        )
        val albumListOffset = args.getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
        )
        val refresh = args.getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, true)

        listModel.viewModelScope.launch(handler) {
            refreshListView?.isRefreshing = true

            if (playlistId != null) {
                setTitle(playlistName!!)
                listModel.getPlaylist(playlistId, playlistName)
            } else if (podcastChannelId != null) {
                setTitle(getString(R.string.podcasts_label))
                listModel.getPodcastEpisodes(podcastChannelId)
            } else if (shareId != null) {
                setTitle(shareName)
                listModel.getShare(shareId)
            } else if (genreName != null) {
                setTitle(genreName)
                listModel.getSongsForGenre(genreName, albumListSize, albumListOffset)
            } else if (getStarredTracks != 0) {
                setTitle(getString(R.string.main_songs_starred))
                listModel.getStarred()
            } else if (getVideos != 0) {
                setTitle(R.string.main_videos)
                listModel.getVideos(refresh)
            } else if (getRandomTracks != 0) {
                setTitle(R.string.main_songs_random)
                listModel.getRandom(albumListSize)
            } else {
                setTitle(name)
                if (!isOffline() && Settings.shouldUseId3Tags) {
                    if (isAlbum) {
                        listModel.getAlbum(refresh, id!!, name, parentId)
                    } else {
                        throw IllegalAccessException("Use AlbumFragment instead!")
                    }
                } else {
                    listModel.getMusicDirectory(refresh, id!!, name, parentId)
                }
            }

            refreshListView?.isRefreshing = false
        }
        return listModel.currentList
    }

    @Suppress("LongMethod")
    override fun onContextMenuItemSelected(
        menuItem: MenuItem,
        item: MusicDirectory.Entry
    ): Boolean {
        val entryId = item.id

        when (menuItem.itemId) {
            R.id.menu_play_now -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = false, append = false,
                    autoPlay = true, shuffle = false, background = false,
                    playNext = false, unpin = false, isArtist = false
                )
            }
            R.id.menu_play_next -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = false, append = false,
                    autoPlay = false, shuffle = false, background = false,
                    playNext = true, unpin = false, isArtist = false
                )
            }
            R.id.menu_play_last -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = false, append = true,
                    autoPlay = false, shuffle = false, background = false,
                    playNext = false, unpin = false, isArtist = false
                )
            }
            R.id.menu_pin -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = true, append = true,
                    autoPlay = false, shuffle = false, background = false,
                    playNext = false, unpin = false, isArtist = false
                )
            }
            R.id.menu_unpin -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = false, append = false,
                    autoPlay = false, shuffle = false, background = false,
                    playNext = false, unpin = true, isArtist = false
                )
            }
            R.id.menu_download -> {
                downloadHandler.downloadRecursively(
                    this, entryId, save = false, append = false,
                    autoPlay = false, shuffle = false, background = true,
                    playNext = false, unpin = false, isArtist = false
                )
            }
            R.id.select_album_play_all -> {
                // TODO: Why is this being handled here?!
                playAll()
            }
            R.id.menu_item_share -> {
                val entries: MutableList<MusicDirectory.Entry?> = ArrayList(1)
                entries.add(item)
                shareHandler.createShare(
                    this, entries, refreshListView,
                    cancellationToken!!
                )
                return true
            }
            else -> {
                return super.onContextItemSelected(menuItem)
            }
        }
        return true
    }

    override fun onItemClick(item: MusicDirectory.Entry) {
        when {
            item.isDirectory -> {
                val bundle = Bundle()
                bundle.putString(Constants.INTENT_EXTRA_NAME_ID, item.id)
                bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, item.isDirectory)
                bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, item.title)
                bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, item.parent)
                Navigation.findNavController(requireView()).navigate(
                    R.id.trackCollectionFragment,
                    bundle
                )
            }
            item.isVideo -> {
                VideoPlayer.playVideo(requireContext(), item)
            }
            else -> {
                enableButtons()
            }
        }
    }
}
