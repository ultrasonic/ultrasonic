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
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.security.SecureRandom
import java.util.Collections
import java.util.Random
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.getTitle
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.CommunicationErrorHandler
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.AlbumHeader
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.AlbumView
import org.moire.ultrasonic.view.EntryAdapter
import org.moire.ultrasonic.view.SongView
import timber.log.Timber

/**
 * Displays a group of tracks, eg. the songs of an album, of a playlist etc.
 * TODO: Refactor this fragment and model to extend the GenericListFragment
 */
@KoinApiExtension
class TrackCollectionFragment : Fragment() {

    private var refreshAlbumListView: SwipeRefreshLayout? = null
    private var albumListView: ListView? = null
    private var header: View? = null
    private var albumButtons: View? = null
    private var emptyView: TextView? = null
    private var selectButton: ImageView? = null
    private var playNowButton: ImageView? = null
    private var playNextButton: ImageView? = null
    private var playLastButton: ImageView? = null
    private var pinButton: ImageView? = null
    private var unpinButton: ImageView? = null
    private var downloadButton: ImageView? = null
    private var deleteButton: ImageView? = null
    private var moreButton: ImageView? = null
    private var playAllButtonVisible = false
    private var shareButtonVisible = false
    private var playAllButton: MenuItem? = null
    private var shareButton: MenuItem? = null

    private val mediaPlayerController: MediaPlayerController by inject()
    private val videoPlayer: VideoPlayer by inject()
    private val downloadHandler: DownloadHandler by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val shareHandler: ShareHandler by inject()
    private var cancellationToken: CancellationToken? = null

    private val model: TrackCollectionModel by viewModels()
    private val random: Random = SecureRandom()

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.select_album, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancellationToken = CancellationToken()

        albumButtons = view.findViewById(R.id.menu_album)

        refreshAlbumListView = view.findViewById(R.id.select_album_entries_refresh)
        albumListView = view.findViewById(R.id.select_album_entries_list)

        refreshAlbumListView!!.setOnRefreshListener {
            updateDisplay(true)
        }

        header = LayoutInflater.from(context).inflate(
            R.layout.select_album_header, albumListView,
            false
        )

        model.currentDirectory.observe(viewLifecycleOwner, defaultObserver)
        model.songsForGenre.observe(viewLifecycleOwner, songsForGenreObserver)

        albumListView!!.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        albumListView!!.setOnItemClickListener { parent, theView, position, _ ->
            if (position >= 0) {
                val entry = parent.getItemAtPosition(position) as MusicDirectory.Entry?
                if (entry != null && entry.isDirectory) {
                    val bundle = Bundle()
                    bundle.putString(Constants.INTENT_EXTRA_NAME_ID, entry.id)
                    bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, entry.isDirectory)
                    bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.title)
                    bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.parent)
                    Navigation.findNavController(theView).navigate(
                        R.id.trackCollectionFragment,
                        bundle
                    )
                } else if (entry != null && entry.isVideo) {
                    videoPlayer.playVideo(requireContext(), entry)
                } else {
                    enableButtons()
                }
            }
        }

        albumListView!!.setOnItemLongClickListener { _, theView, _, _ ->
            if (theView is AlbumView) {
                return@setOnItemLongClickListener false
            }
            if (theView is SongView) {
                theView.maximizeOrMinimize()
                return@setOnItemLongClickListener true
            }
            return@setOnItemLongClickListener false
        }

        selectButton = view.findViewById(R.id.select_album_select)
        playNowButton = view.findViewById(R.id.select_album_play_now)
        playNextButton = view.findViewById(R.id.select_album_play_next)
        playLastButton = view.findViewById(R.id.select_album_play_last)
        pinButton = view.findViewById(R.id.select_album_pin)
        unpinButton = view.findViewById(R.id.select_album_unpin)
        downloadButton = view.findViewById(R.id.select_album_download)
        deleteButton = view.findViewById(R.id.select_album_delete)
        moreButton = view.findViewById(R.id.select_album_more)
        emptyView = TextView(requireContext())

        selectButton!!.setOnClickListener {
            selectAllOrNone()
        }
        playNowButton!!.setOnClickListener {
            playNow(false)
        }
        playNextButton!!.setOnClickListener {
            downloadHandler.download(
                this@TrackCollectionFragment, append = true,
                save = false, autoPlay = false, playNext = true, shuffle = false,
                songs = getSelectedSongs(albumListView)
            )
            selectAll(selected = false, toast = false)
        }
        playLastButton!!.setOnClickListener {
            playNow(true)
        }
        pinButton!!.setOnClickListener {
            downloadBackground(true)
            selectAll(selected = false, toast = false)
        }
        unpinButton!!.setOnClickListener {
            unpin()
            selectAll(selected = false, toast = false)
        }
        downloadButton!!.setOnClickListener {
            downloadBackground(false)
            selectAll(selected = false, toast = false)
        }
        deleteButton!!.setOnClickListener {
            delete()
            selectAll(selected = false, toast = false)
        }

        registerForContextMenu(albumListView!!)
        setHasOptionsMenu(true)
        enableButtons()
        updateDisplay(false)
    }

    val handler = CoroutineExceptionHandler { _, exception ->
        Handler(Looper.getMainLooper()).post {
            context?.let { CommunicationErrorHandler.handleError(exception, it) }
        }
        refreshAlbumListView!!.isRefreshing = false
    }

    private fun updateDisplay(refresh: Boolean) {
        val args = requireArguments()
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

        fun setTitle(name: String?) {
            setTitle(this@TrackCollectionFragment, name)
        }

        fun setTitle(name: Int) {
            setTitle(this@TrackCollectionFragment, name)
        }

        model.viewModelScope.launch(handler) {
            refreshAlbumListView!!.isRefreshing = true

            model.getMusicFolders(refresh)

            if (playlistId != null) {
                setTitle(playlistName)
                model.getPlaylist(playlistId, playlistName)
            } else if (podcastChannelId != null) {
                setTitle(getString(R.string.podcasts_label))
                model.getPodcastEpisodes(podcastChannelId)
            } else if (shareId != null) {
                setTitle(shareName)
                model.getShare(shareId)
            } else if (genreName != null) {
                setTitle(genreName)
                model.getSongsForGenre(genreName, albumListSize, albumListOffset)
            } else if (getStarredTracks != 0) {
                setTitle(getString(R.string.main_songs_starred))
                model.getStarred()
            } else if (getVideos != 0) {
                setTitle(R.string.main_videos)
                model.getVideos(refresh)
            } else if (getRandomTracks != 0) {
                setTitle(R.string.main_songs_random)
                model.getRandom(albumListSize)
            } else {
                setTitle(name)
                if (!isOffline() && Util.getShouldUseId3Tags()) {
                    if (isAlbum) {
                        model.getAlbum(refresh, id, name, parentId)
                    } else {
                        model.getArtist(refresh, id, name)
                    }
                } else {
                    model.getMusicDirectory(refresh, id, name, parentId)
                }
            }

            refreshAlbumListView!!.isRefreshing = false
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val info = menuInfo as AdapterContextMenuInfo?

        val entry = albumListView!!.getItemAtPosition(info!!.position) as MusicDirectory.Entry?

        if (entry != null && entry.isDirectory) {
            val inflater = requireActivity().menuInflater
            inflater.inflate(R.menu.generic_context_menu, menu)
        }

        shareButton = menu.findItem(R.id.menu_item_share)

        if (shareButton != null) {
            shareButton!!.isVisible = !isOffline()
        }

        val downloadMenuItem = menu.findItem(R.id.menu_download)
        if (downloadMenuItem != null) {
            downloadMenuItem.isVisible = !isOffline()
        }
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        Timber.d("onContextItemSelected")
        val info = menuItem.menuInfo as AdapterContextMenuInfo? ?: return true

        val entry = albumListView!!.getItemAtPosition(info.position) as MusicDirectory.Entry?
            ?: return true

        val entryId = entry.id

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
                entries.add(entry)
                shareHandler.createShare(
                    this, entries, refreshAlbumListView,
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
                this, getSelectedSongs(albumListView),
                refreshAlbumListView, cancellationToken!!
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
        val selectedSongs = getSelectedSongs(albumListView)

        if (selectedSongs.isNotEmpty()) {
            downloadHandler.download(
                this, append, false, !append, playNext = false,
                shuffle = false, songs = selectedSongs
            )
            selectAll(selected = false, toast = false)
        } else {
            playAll(false, append)
        }
    }

    private fun playAll(shuffle: Boolean = false, append: Boolean = false) {
        var hasSubFolders = false

        for (i in 0 until albumListView!!.count) {
            val entry = albumListView!!.getItemAtPosition(i) as MusicDirectory.Entry?
            if (entry != null && entry.isDirectory) {
                hasSubFolders = true
                break
            }
        }

        val isArtist = requireArguments().getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false)
        val id = requireArguments().getString(Constants.INTENT_EXTRA_NAME_ID)

        if (hasSubFolders && id != null) {
            downloadHandler.downloadRecursively(
                this, id, false, append, !append,
                shuffle, background = false, playNext = false, unpin = false, isArtist = isArtist
            )
        } else {
            selectAll(selected = true, toast = false)
            downloadHandler.download(
                this, append, false, !append, false,
                shuffle, getSelectedSongs(albumListView)
            )
            selectAll(selected = false, toast = false)
        }
    }

    private fun selectAllOrNone() {
        var someUnselected = false
        val count = albumListView!!.count

        for (i in 0 until count) {
            if (!albumListView!!.isItemChecked(i) &&
                albumListView!!.getItemAtPosition(i) is MusicDirectory.Entry
            ) {
                someUnselected = true
                break
            }
        }
        selectAll(someUnselected, true)
    }

    private fun selectAll(selected: Boolean, toast: Boolean) {
        val count = albumListView!!.count
        var selectedCount = 0

        for (i in 0 until count) {
            val entry = albumListView!!.getItemAtPosition(i) as MusicDirectory.Entry?
            if (entry != null && !entry.isDirectory && !entry.isVideo) {
                albumListView!!.setItemChecked(i, selected)
                selectedCount++
            }
        }

        // Display toast: N tracks selected / N tracks unselected
        if (toast) {
            val toastResId = if (selected)
                R.string.select_album_n_selected
            else
                R.string.select_album_n_unselected
            Util.toast(activity, getString(toastResId, selectedCount))
        }
        enableButtons()
    }

    private fun enableButtons() {
        val selection = getSelectedSongs(albumListView)
        val enabled = selection.isNotEmpty()
        var unpinEnabled = false
        var deleteEnabled = false

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

        playNowButton!!.visibility = if (enabled) View.VISIBLE else View.GONE
        playNextButton!!.visibility = if (enabled) View.VISIBLE else View.GONE
        playLastButton!!.visibility = if (enabled) View.VISIBLE else View.GONE
        pinButton!!.visibility = if (enabled && !isOffline() && selection.size > pinnedCount)
            View.VISIBLE
        else
            View.GONE
        unpinButton!!.visibility = if (enabled && unpinEnabled) View.VISIBLE else View.GONE
        downloadButton!!.visibility = if (enabled && !deleteEnabled && !isOffline())
            View.VISIBLE
        else
            View.GONE
        deleteButton!!.visibility = if (enabled && deleteEnabled) View.VISIBLE else View.GONE
    }

    private fun downloadBackground(save: Boolean) {
        var songs = getSelectedSongs(albumListView)

        if (songs.isEmpty()) {
            selectAll(selected = true, toast = false)
            songs = getSelectedSongs(albumListView)
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

    private fun delete() {
        var songs = getSelectedSongs(albumListView)

        if (songs.isEmpty()) {
            selectAll(selected = true, toast = false)
            songs = getSelectedSongs(albumListView)
        }

        mediaPlayerController.delete(songs)
    }

    private fun unpin() {
        val songs = getSelectedSongs(albumListView)
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
        if (musicDirectory.getChildren().size < requireArguments().getInt(
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

        updateInterfaceWithEntries(musicDirectory)
    }

    private val defaultObserver = Observer(this::updateInterfaceWithEntries)

    private fun updateInterfaceWithEntries(musicDirectory: MusicDirectory) {
        val entries = musicDirectory.getChildren()

        if (model.currentListIsSortable && Util.getShouldSortByDisc()) {
            Collections.sort(entries, EntryByDiscAndTrackComparator())
        }

        var allVideos = true
        var songCount = 0

        for (entry in entries) {
            if (!entry.isVideo) {
                allVideos = false
            }
            if (!entry.isDirectory) {
                songCount++
            }
        }

        val listSize = requireArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0)

        if (songCount > 0) {
            if (model.showHeader) {
                val intentAlbumName = requireArguments().getString(Constants.INTENT_EXTRA_NAME_NAME)
                val directoryName = musicDirectory.name
                val header = createHeader(
                    entries, intentAlbumName ?: directoryName,
                    songCount
                )
                if (header != null && albumListView!!.headerViewsCount == 0) {
                    albumListView!!.addHeaderView(header, null, false)
                }
            }

            pinButton!!.visibility = View.VISIBLE
            unpinButton!!.visibility = View.VISIBLE
            downloadButton!!.visibility = View.VISIBLE
            deleteButton!!.visibility = View.VISIBLE
            selectButton!!.visibility = if (allVideos) View.GONE else View.VISIBLE
            playNowButton!!.visibility = View.VISIBLE
            playNextButton!!.visibility = View.VISIBLE
            playLastButton!!.visibility = View.VISIBLE

            if (listSize == 0 || songCount < listSize) {
                moreButton!!.visibility = View.GONE
            } else {
                moreButton!!.visibility = View.VISIBLE
                if (requireArguments().getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0) > 0) {
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
        } else {

            // TODO: This code path can be removed when getArtist has been moved to
            // AlbumListFragment (getArtist returns the albums of an artist)
            pinButton!!.visibility = View.GONE
            unpinButton!!.visibility = View.GONE
            downloadButton!!.visibility = View.GONE
            deleteButton!!.visibility = View.GONE
            selectButton!!.visibility = View.GONE
            playNowButton!!.visibility = View.GONE
            playNextButton!!.visibility = View.GONE
            playLastButton!!.visibility = View.GONE

            if (listSize == 0 || musicDirectory.getChildren().size < listSize) {
                albumButtons!!.visibility = View.GONE
            } else {
                moreButton!!.visibility = View.VISIBLE
            }
        }

        enableButtons()

        val isAlbumList = requireArguments().containsKey(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE
        )

        playAllButtonVisible = !(isAlbumList || entries.isEmpty()) && !allVideos
        shareButtonVisible = !isOffline() && songCount > 0

        albumListView!!.removeHeaderView(emptyView!!)
        if (entries.isEmpty()) {
            emptyView!!.text = getString(R.string.select_album_empty)
            emptyView!!.setPadding(10, 10, 10, 10)
            albumListView!!.addHeaderView(emptyView, null, false)
        }

        if (playAllButton != null) {
            playAllButton!!.isVisible = playAllButtonVisible
        }

        if (shareButton != null) {
            shareButton!!.isVisible = shareButtonVisible
        }

        albumListView!!.adapter = EntryAdapter(
            context,
            imageLoaderProvider.getImageLoader(), entries, true
        )

        val playAll = requireArguments().getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
        if (playAll && songCount > 0) {
            playAll(
                requireArguments().getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false),
                false
            )
        }

        model.currentListIsSortable = true
    }

    private fun createHeader(
        entries: List<MusicDirectory.Entry>,
        name: CharSequence?,
        songCount: Int
    ): View? {
        val coverArtView = header!!.findViewById<View>(R.id.select_album_art) as ImageView
        val artworkSelection = random.nextInt(entries.size)
        imageLoaderProvider.getImageLoader().loadImage(
            coverArtView, entries[artworkSelection], false,
            Util.getAlbumImageSize(context), false, true
        )

        val albumHeader = AlbumHeader.processEntries(context, entries)

        val titleView = header!!.findViewById<View>(R.id.select_album_title) as TextView
        titleView.text = name ?: getTitle(this@TrackCollectionFragment) // getActionBarSubtitle());

        // Don't show a header if all entries are videos
        if (albumHeader.isAllVideo) {
            return null
        }

        val artistView = header!!.findViewById<TextView>(R.id.select_album_artist)

        val artist: String = when {
            albumHeader.artists.size == 1 -> albumHeader.artists.iterator().next()
            albumHeader.grandParents.size == 1 -> albumHeader.grandParents.iterator().next()
            else -> resources.getString(R.string.common_various_artists)
        }

        artistView.text = artist

        val genreView = header!!.findViewById<TextView>(R.id.select_album_genre)

        val genre: String = if (albumHeader.genres.size == 1)
            albumHeader.genres.iterator().next()
        else
            resources.getString(R.string.common_multiple_genres)

        genreView.text = genre

        val yearView = header!!.findViewById<TextView>(R.id.select_album_year)

        val year: String = if (albumHeader.years.size == 1)
            albumHeader.years.iterator().next().toString()
        else
            resources.getString(R.string.common_multiple_years)

        yearView.text = year

        val songCountView = header!!.findViewById<TextView>(R.id.select_album_song_count)
        val songs = resources.getQuantityString(
            R.plurals.select_album_n_songs, songCount,
            songCount
        )
        songCountView.text = songs

        val duration = Util.formatTotalDuration(albumHeader.totalDuration)

        val durationView = header!!.findViewById<TextView>(R.id.select_album_duration)
        durationView.text = duration

        return header
    }

    private fun getSelectedSongs(albumListView: ListView?): List<MusicDirectory.Entry?> {
        val songs: MutableList<MusicDirectory.Entry?> = ArrayList(10)

        if (albumListView != null) {
            val count = albumListView.count
            for (i in 0 until count) {
                if (albumListView.isItemChecked(i)) {
                    songs.add(albumListView.getItemAtPosition(i) as MusicDirectory.Entry?)
                }
            }
        }

        return songs
    }
}
