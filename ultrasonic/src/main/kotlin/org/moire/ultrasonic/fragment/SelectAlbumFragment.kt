package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import java.security.SecureRandom
import java.util.Collections
import java.util.LinkedList
import java.util.Random
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.getTitle
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.subsonic.DownloadHandler
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.AlbumHeader
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.FragmentBackgroundTask
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.AlbumView
import org.moire.ultrasonic.view.EntryAdapter
import org.moire.ultrasonic.view.SelectMusicFolderView
import org.moire.ultrasonic.view.SongView
import timber.log.Timber

/**
 * Displays a group of playable media from the library, which can be an Album, a Playlist, etc.
 */
class SelectAlbumFragment : Fragment() {

    private val allSongsId = "-1"
    private var refreshAlbumListView: SwipeRefreshLayout? = null
    private var albumListView: ListView? = null
    private var header: View? = null
    private var selectFolderHeader: SelectMusicFolderView? = null
    private var albumButtons: View? = null
    private var emptyView: View? = null
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
    private var showHeader = true
    private var showSelectFolderHeader = false
    private val random: Random = SecureRandom()

    private val mediaPlayerController: MediaPlayerController by inject()
    private val videoPlayer: VideoPlayer by inject()
    private val downloadHandler: DownloadHandler by inject()
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val shareHandler: ShareHandler by inject()
    private var cancellationToken: CancellationToken? = null
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val artistListModel: ArtistListModel by viewModel()

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

        refreshAlbumListView!!.setOnRefreshListener(
            OnRefreshListener
            {
                updateDisplay(true)
            }
        )

        header = LayoutInflater.from(context).inflate(
            R.layout.select_album_header, albumListView,
            false
        )

        selectFolderHeader = SelectMusicFolderView(
            requireContext(), albumListView!!,
            MusicServiceFactory.getMusicService(requireContext()).getMusicFolders(
                false, requireContext()
            ),
            activeServerProvider.getActiveServer().musicFolderId,
            { _, selectedFolderId ->
                if (!ActiveServerProvider.isOffline(context)) {
                    val currentSetting = activeServerProvider.getActiveServer()
                    currentSetting.musicFolderId = selectedFolderId
                    serverSettingsModel.updateItem(currentSetting)
                }
                artistListModel.refresh(refreshAlbumListView!!)

                this.updateDisplay(true)
            }
        )

        albumListView!!.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
        albumListView!!.setOnItemClickListener(
            OnItemClickListener
            { parent, theView, position, _ ->
                if (position >= 0) {
                    val entry = parent.getItemAtPosition(position) as MusicDirectory.Entry?
                    if (entry != null && entry.isDirectory) {
                        val bundle = Bundle()
                        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, entry.id)
                        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, entry.isDirectory)
                        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.title)
                        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.parent)
                        Navigation.findNavController(theView).navigate(
                            R.id.selectAlbumFragment,
                            bundle
                        )
                    } else if (entry != null && entry.isVideo) {
                        videoPlayer.playVideo(requireContext(), entry)
                    } else {
                        enableButtons()
                    }
                }
            }
        )

        // TODO Long click on an item will first try to maximize / collapse the item, even when it
        // fits inside the TextView. The context menu is only displayed on the second long click...
        // This may be improved somehow, e.g. checking first if the texts fit
        albumListView!!.setOnItemLongClickListener(
            OnItemLongClickListener
            { _, theView, _, _ ->
                if (theView is AlbumView) {
                    val albumView = theView
                    if (!albumView.isMaximized) {
                        albumView.maximizeOrMinimize()
                        return@OnItemLongClickListener true
                    } else {
                        return@OnItemLongClickListener false
                    }
                }
                if (theView is SongView) {
                    theView.maximizeOrMinimize()
                    return@OnItemLongClickListener true
                }
                false
            }
        )

        selectButton = view.findViewById(R.id.select_album_select)
        playNowButton = view.findViewById(R.id.select_album_play_now)
        playNextButton = view.findViewById(R.id.select_album_play_next)
        playLastButton = view.findViewById(R.id.select_album_play_last)
        pinButton = view.findViewById(R.id.select_album_pin)
        unpinButton = view.findViewById(R.id.select_album_unpin)
        downloadButton = view.findViewById(R.id.select_album_download)
        deleteButton = view.findViewById(R.id.select_album_delete)
        moreButton = view.findViewById(R.id.select_album_more)
        emptyView = view.findViewById(R.id.select_album_empty)

        selectButton!!.setOnClickListener(
            View.OnClickListener
            {
                selectAllOrNone()
            }
        )
        playNowButton!!.setOnClickListener(
            View.OnClickListener
            {
                playNow(false)
            }
        )
        playNextButton!!.setOnClickListener(
            View.OnClickListener
            {
                downloadHandler.download(
                    this@SelectAlbumFragment, true,
                    false, false, true, false,
                    getSelectedSongs(albumListView)
                )
                selectAll(false, false)
            }
        )
        playLastButton!!.setOnClickListener(
            View.OnClickListener
            {
                playNow(true)
            }
        )
        pinButton!!.setOnClickListener(
            View.OnClickListener
            {
                downloadBackground(true)
                selectAll(false, false)
            }
        )
        unpinButton!!.setOnClickListener(
            View.OnClickListener
            {
                unpin()
                selectAll(false, false)
            }
        )
        downloadButton!!.setOnClickListener(
            View.OnClickListener
            {
                downloadBackground(false)
                selectAll(false, false)
            }
        )
        deleteButton!!.setOnClickListener(
            View.OnClickListener
            {
                delete()
                selectAll(false, false)
            }
        )

        registerForContextMenu(albumListView!!)
        setHasOptionsMenu(true)
        enableButtons()
        updateDisplay(false)
    }

    private fun updateDisplay(refresh: Boolean) {
        val id = requireArguments().getString(Constants.INTENT_EXTRA_NAME_ID)
        val isAlbum = requireArguments().getBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false)
        val name = requireArguments().getString(Constants.INTENT_EXTRA_NAME_NAME)
        val parentId = requireArguments().getString(Constants.INTENT_EXTRA_NAME_PARENT_ID)
        val playlistId = requireArguments().getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID)
        val podcastChannelId = requireArguments().getString(
            Constants.INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID
        )
        val playlistName = requireArguments().getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME)
        val shareId = requireArguments().getString(Constants.INTENT_EXTRA_NAME_SHARE_ID)
        val shareName = requireArguments().getString(Constants.INTENT_EXTRA_NAME_SHARE_NAME)
        val albumListType = requireArguments().getString(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE
        )
        val genreName = requireArguments().getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME)
        val albumListTitle = requireArguments().getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0
        )
        val getStarredTracks = requireArguments().getInt(Constants.INTENT_EXTRA_NAME_STARRED, 0)
        val getVideos = requireArguments().getInt(Constants.INTENT_EXTRA_NAME_VIDEOS, 0)
        val getRandomTracks = requireArguments().getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0)
        val albumListSize = requireArguments().getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
        )
        val albumListOffset = requireArguments().getInt(
            Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
        )

        if (playlistId != null) {
            getPlaylist(playlistId, playlistName)
        } else if (podcastChannelId != null) {
            getPodcastEpisodes(podcastChannelId)
        } else if (shareId != null) {
            getShare(shareId, shareName)
        } else if (albumListType != null) {
            getAlbumList(albumListType, albumListTitle, albumListSize, albumListOffset)
        } else if (genreName != null) {
            getSongsForGenre(genreName, albumListSize, albumListOffset)
        } else if (getStarredTracks != 0) {
            starred
        } else if (getVideos != 0) {
            getVideos(refresh)
        } else if (getRandomTracks != 0) {
            getRandom(albumListSize)
        } else {
            if (!isOffline(activity) && Util.getShouldUseId3Tags(activity)) {
                if (isAlbum) {
                    getAlbum(refresh, id, name, parentId)
                } else {
                    getArtist(refresh, id, name)
                }
            } else {
                getMusicDirectory(refresh, id, name, parentId)
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        val info = menuInfo as AdapterContextMenuInfo?

        val entry = albumListView!!.getItemAtPosition(info!!.position) as MusicDirectory.Entry?

        if (entry != null && entry.isDirectory) {
            val inflater = requireActivity().menuInflater
            inflater.inflate(R.menu.select_album_context, menu)
        }

        shareButton = menu.findItem(R.id.menu_item_share)

        if (shareButton != null) {
            shareButton!!.isVisible = !isOffline(context)
        }

        val downloadMenuItem = menu.findItem(R.id.album_menu_download)
        if (downloadMenuItem != null) {
            downloadMenuItem.isVisible = !isOffline(context)
        }
    }

    override fun onContextItemSelected(menuItem: MenuItem): Boolean {
        Timber.d("onContextItemSelected")
        val info = menuItem.menuInfo as AdapterContextMenuInfo? ?: return true

        val entry = albumListView!!.getItemAtPosition(info.position) as MusicDirectory.Entry?
            ?: return true

        val entryId = entry.id

        val itemId = menuItem.itemId
        if (itemId == R.id.album_menu_play_now) {
            downloadHandler.downloadRecursively(
                this, entryId, false, false, true, false, false, false, false, false
            )
        } else if (itemId == R.id.album_menu_play_next) {
            downloadHandler.downloadRecursively(
                this, entryId, false, false, false, false, false, true, false, false
            )
        } else if (itemId == R.id.album_menu_play_last) {
            downloadHandler.downloadRecursively(
                this, entryId, false, true, false, false, false, false, false, false
            )
        } else if (itemId == R.id.album_menu_pin) {
            downloadHandler.downloadRecursively(
                this, entryId, true, true, false, false, false, false, false, false
            )
        } else if (itemId == R.id.album_menu_unpin) {
            downloadHandler.downloadRecursively(
                this, entryId, false, false, false, false, false, false, true, false
            )
        } else if (itemId == R.id.album_menu_download) {
            downloadHandler.downloadRecursively(
                this, entryId, false, false, false, false, true, false, false, false
            )
        } else if (itemId == R.id.select_album_play_all) {
            playAll()
        } else if (itemId == R.id.menu_item_share) {
            val entries: MutableList<MusicDirectory.Entry?> = ArrayList(1)
            entries.add(entry)
            shareHandler.createShare(
                this, entries, refreshAlbumListView,
                cancellationToken!!
            )
            return true
        } else {
            return super.onContextItemSelected(menuItem)
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

        if (!selectedSongs.isEmpty()) {
            downloadHandler.download(
                this, append, false, !append, false,
                false, selectedSongs
            )
            selectAll(false, false)
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
                shuffle, false, false, false, isArtist
            )
        } else {
            selectAll(true, false)
            downloadHandler.download(
                this, append, false, !append, false,
                shuffle, getSelectedSongs(albumListView)
            )
            selectAll(false, false)
        }
    }

    private fun getMusicDirectory(refresh: Boolean, id: String?, name: String?, parentId: String?) {
        setTitle(this, name)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                var root = MusicDirectory()

                if (allSongsId == id) {
                    val musicDirectory = service.getMusicDirectory(parentId, name, refresh, context)

                    val songs: MutableList<MusicDirectory.Entry> = LinkedList()
                    getSongsRecursively(musicDirectory, songs)

                    for (song in songs) {
                        if (!song.isDirectory) {
                            root.addChild(song)
                        }
                    }
                } else {
                    val musicDirectory = service.getMusicDirectory(id, name, refresh, context)

                    if (Util.getShouldShowAllSongsByArtist(context) &&
                        musicDirectory.findChild(allSongsId) == null &&
                        musicDirectory.getChildren(true, false).size ==
                        musicDirectory.getChildren(true, true).size
                    ) {
                        val allSongs = MusicDirectory.Entry()

                        allSongs.isDirectory = true
                        allSongs.artist = name
                        allSongs.parent = id
                        allSongs.id = allSongsId
                        allSongs.title = String.format(
                            resources.getString(R.string.select_album_all_songs), name
                        )

                        root.addChild(allSongs)
                        root.addAll(musicDirectory.getChildren())
                    } else {
                        root = musicDirectory
                    }
                }
                return root
            }

            private fun getSongsRecursively(
                parent: MusicDirectory,
                songs: MutableList<MusicDirectory.Entry>
            ) {
                for (song in parent.getChildren(false, true)) {
                    if (!song.isVideo && !song.isDirectory) {
                        songs.add(song)
                    }
                }

                val musicService = getMusicService(context!!)

                for ((id1, _, _, title) in parent.getChildren(true, false)) {
                    var root: MusicDirectory

                    if (allSongsId != id1) {
                        root = musicService.getMusicDirectory(id1, title, false, context)

                        getSongsRecursively(root, songs)
                    }
                }
            }
        }.execute()
    }

    private fun getArtist(refresh: Boolean, id: String?, name: String?) {
        setTitle(this, name)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {

                var root = MusicDirectory()

                val musicDirectory = service.getArtist(id, name, refresh, context)

                if (Util.getShouldShowAllSongsByArtist(context) &&
                    musicDirectory.findChild(allSongsId) == null &&
                    musicDirectory.getChildren(true, false).size ==
                    musicDirectory.getChildren(true, true).size
                ) {
                    val allSongs = MusicDirectory.Entry()

                    allSongs.isDirectory = true
                    allSongs.artist = name
                    allSongs.parent = id
                    allSongs.id = allSongsId
                    allSongs.title = String.format(
                        resources.getString(R.string.select_album_all_songs), name
                    )

                    root.addFirst(allSongs)
                    root.addAll(musicDirectory.getChildren())
                } else {
                    root = musicDirectory
                }
                return root
            }
        }.execute()
    }

    private fun getAlbum(refresh: Boolean, id: String?, name: String?, parentId: String?) {
        setTitle(this, name)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {

                val musicDirectory: MusicDirectory

                musicDirectory = if (allSongsId == id) {
                    val root = MusicDirectory()

                    val songs: MutableCollection<MusicDirectory.Entry> = LinkedList()
                    getSongsForArtist(parentId, songs)

                    for (song in songs) {
                        if (!song.isDirectory) {
                            root.addChild(song)
                        }
                    }
                    root
                } else {
                    service.getAlbum(id, name, refresh, context)
                }
                return musicDirectory
            }

            private fun getSongsForArtist(
                id: String?,
                songs: MutableCollection<MusicDirectory.Entry>
            ) {

                val musicService = getMusicService(context!!)
                val artist = musicService.getArtist(id, "", false, context)

                for ((id1) in artist.getChildren()) {
                    if (allSongsId != id1) {
                        val albumDirectory = musicService.getAlbum(id1, "", false, context)

                        for (song in albumDirectory.getChildren()) {
                            if (!song.isVideo) {
                                songs.add(song)
                            }
                        }
                    }
                }
            }
        }.execute()
    }

    private fun getSongsForGenre(genre: String, count: Int, offset: Int) {
        setTitle(this, genre)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                return service.getSongsByGenre(genre, count, offset, context)
            }

            override fun done(result: Pair<MusicDirectory, Boolean>) {
                // Hide more button when results are less than album list size
                if (result.first.getChildren().size < arguments!!.getInt(
                    Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
                )
                ) {
                    moreButton!!.visibility = View.GONE
                } else {
                    moreButton!!.visibility = View.VISIBLE
                }

                moreButton!!.setOnClickListener {
                    val theGenre = arguments!!.getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME)
                    val size = arguments!!.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0)
                    val theOffset = arguments!!.getInt(
                        Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
                    ) + size
                    val bundle = Bundle()
                    bundle.putString(Constants.INTENT_EXTRA_NAME_GENRE_NAME, theGenre)
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size)
                    bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, theOffset)
                    Navigation.findNavController(view!!).navigate(R.id.selectAlbumFragment, bundle)
                }

                super.done(result)
            }
        }.execute()
    }

    private val starred: Unit
        get() {
            setTitle(this, R.string.main_songs_starred)

            object : LoadTask() {
                override fun load(service: MusicService): MusicDirectory {
                    return if (Util.getShouldUseId3Tags(context))
                        Util.getSongsFromSearchResult(service.getStarred2(context))
                    else
                        Util.getSongsFromSearchResult(service.getStarred(context))
                }
            }.execute()
        }

    private fun getVideos(refresh: Boolean) {
        showHeader = false

        setTitle(this, R.string.main_videos)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                return service.getVideos(refresh, context)
            }
        }.execute()
    }

    private fun getRandom(size: Int) {
        setTitle(this, R.string.main_songs_random)

        object : LoadTask() {
            override fun sortableCollection(): Boolean {
                return false
            }

            override fun load(service: MusicService): MusicDirectory {
                return service.getRandomSongs(size, context)
            }
        }.execute()
    }

    private fun getPlaylist(playlistId: String, playlistName: String?) {

        setTitle(this, playlistName)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                return service.getPlaylist(playlistId, playlistName, context)
            }
        }.execute()
    }

    private fun getPodcastEpisodes(podcastChannelId: String) {

        setTitle(this, R.string.podcasts_label)

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                return service.getPodcastEpisodes(podcastChannelId, context)
            }
        }.execute()
    }

    private fun getShare(shareId: String, shareName: CharSequence?) {

        setTitle(this, shareName)
        // setActionBarSubtitle(shareName);

        object : LoadTask() {
            override fun load(service: MusicService): MusicDirectory {
                val shares = service.getShares(true, context)

                val md = MusicDirectory()

                for (share in shares) {
                    if (share.id == shareId) {
                        for (entry in share.getEntries()) {
                            md.addChild(entry)
                        }
                        break
                    }
                }
                return md
            }
        }.execute()
    }

    private fun getAlbumList(albumListType: String, albumListTitle: Int, size: Int, offset: Int) {

        showHeader = false
        showSelectFolderHeader = !isOffline(context) &&
            (
                (albumListType == AlbumListType.SORTED_BY_NAME.toString()) ||
                    (albumListType == AlbumListType.SORTED_BY_ARTIST.toString())
                )

        setTitle(this, albumListTitle)
        // setActionBarSubtitle(albumListTitle);

        object : LoadTask() {
            override fun sortableCollection(): Boolean {
                return albumListType != "newest" && albumListType != "random" &&
                    albumListType != "highest" && albumListType != "recent" &&
                    albumListType != "frequent"
            }

            override fun load(service: MusicService): MusicDirectory {
                val musicFolderId =
                    this@SelectAlbumFragment.activeServerProvider.getActiveServer().musicFolderId
                return if (Util.getShouldUseId3Tags(context))
                    service.getAlbumList2(albumListType, size, offset, context)
                else
                    service.getAlbumList(albumListType, size, offset, musicFolderId, context)
            }

            override fun done(result: Pair<MusicDirectory, Boolean>) {
                if (!result.first.getChildren().isEmpty()) {
                    pinButton!!.visibility = View.GONE
                    unpinButton!!.visibility = View.GONE
                    downloadButton!!.visibility = View.GONE
                    deleteButton!!.visibility = View.GONE

                    // Hide more button when results are less than album list size
                    if (result.first.getChildren().size < arguments!!.getInt(
                        Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
                    )
                    ) {
                        moreButton!!.visibility = View.GONE
                    } else {
                        moreButton!!.visibility = View.VISIBLE
                        moreButton!!.setOnClickListener {
                            val theAlbumListTitle = arguments!!.getInt(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0
                            )
                            val type = arguments!!.getString(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE
                            )
                            val theSize = arguments!!.getInt(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0
                            )
                            val theOffset = arguments!!.getInt(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
                            ) + theSize

                            val bundle = Bundle()
                            bundle.putInt(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, theAlbumListTitle
                            )
                            bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type)
                            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, theSize)
                            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, theOffset)
                            Navigation.findNavController(view!!).navigate(
                                R.id.selectAlbumFragment, bundle
                            )
                        }
                    }
                } else {
                    moreButton!!.visibility = View.GONE
                }
                super.done(result)
            }
        }.execute()
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
        val enabled = !selection.isEmpty()
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
        pinButton!!.visibility = if (enabled && !isOffline(context) && selection.size > pinnedCount)
            View.VISIBLE
        else
            View.GONE
        unpinButton!!.visibility = if (enabled && unpinEnabled) View.VISIBLE else View.GONE
        downloadButton!!.visibility = if (enabled && !deleteEnabled && !isOffline(context))
            View.VISIBLE
        else
            View.GONE
        deleteButton!!.visibility = if (enabled && deleteEnabled) View.VISIBLE else View.GONE
    }

    private fun downloadBackground(save: Boolean) {
        var songs = getSelectedSongs(albumListView)

        if (songs.isEmpty()) {
            selectAll(true, false)
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
            selectAll(true, false)
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

    private abstract inner class LoadTask : FragmentBackgroundTask<Pair<MusicDirectory, Boolean>>(
        this@SelectAlbumFragment.activity, true, refreshAlbumListView,
        cancellationToken
    ) {

        protected abstract fun load(service: MusicService): MusicDirectory
        protected open fun sortableCollection(): Boolean {
            return true
        }

        override fun doInBackground(): Pair<MusicDirectory, Boolean> {
            val musicService = getMusicService(context!!)
            val dir = load(musicService)
            val valid = musicService.isLicenseValid(context)
            return Pair<MusicDirectory, Boolean>(dir, valid)
        }

        protected override fun done(result: Pair<MusicDirectory, Boolean>) {
            val musicDirectory = result.first
            val entries = musicDirectory.getChildren()

            if (sortableCollection() && Util.getShouldSortByDisc(context)) {
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

            val listSize = arguments!!.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0)

            if (songCount > 0) {
                if (showHeader) {
                    val intentAlbumName = arguments!!.getString(Constants.INTENT_EXTRA_NAME_NAME)
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
                    if (arguments!!.getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0) > 0) {
                        moreButton!!.setOnClickListener {
                            val offset = arguments!!.getInt(
                                Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0
                            ) + listSize
                            val bundle = Bundle()
                            bundle.putInt(Constants.INTENT_EXTRA_NAME_RANDOM, 1)
                            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, listSize)
                            bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset)
                            Navigation.findNavController(view!!).navigate(
                                R.id.selectAlbumFragment, bundle
                            )
                        }
                    }
                }
            } else {
                if (showSelectFolderHeader) {
                    if (albumListView!!.headerViewsCount == 0) {
                        albumListView!!.addHeaderView(selectFolderHeader!!.itemView, null, false)
                    }
                }

                pinButton!!.visibility = View.GONE
                unpinButton!!.visibility = View.GONE
                downloadButton!!.visibility = View.GONE
                deleteButton!!.visibility = View.GONE
                selectButton!!.visibility = View.GONE
                playNowButton!!.visibility = View.GONE
                playNextButton!!.visibility = View.GONE
                playLastButton!!.visibility = View.GONE

                if (listSize == 0 || result.first.getChildren().size < listSize) {
                    albumButtons!!.visibility = View.GONE
                } else {
                    moreButton!!.visibility = View.VISIBLE
                }
            }

            enableButtons()

            val isAlbumList = arguments!!.containsKey(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE)
            playAllButtonVisible = !(isAlbumList || entries.isEmpty()) && !allVideos
            shareButtonVisible = !isOffline(context) && songCount > 0

            emptyView!!.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

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

            val playAll = arguments!!.getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false)
            if (playAll && songCount > 0) {
                playAll(
                    arguments!!.getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false),
                    false
                )
            }
        }

        protected fun createHeader(
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
            titleView.text = name ?: getTitle(this@SelectAlbumFragment) // getActionBarSubtitle());

            // Don't show a header if all entries are videos
            if (albumHeader.isAllVideo) {
                return null
            }

            val artistView = header!!.findViewById<TextView>(R.id.select_album_artist)
            val artist: String

            artist = if (albumHeader.artists.size == 1)
                albumHeader.artists.iterator().next()
            else if (albumHeader.grandParents.size == 1)
                albumHeader.grandParents.iterator().next()
            else
                resources.getString(R.string.common_various_artists)

            artistView.text = artist

            val genreView = header!!.findViewById<TextView>(R.id.select_album_genre)
            val genre: String

            genre = if (albumHeader.genres.size == 1)
                albumHeader.genres.iterator().next()
            else
                resources.getString(R.string.common_multiple_genres)

            genreView.text = genre

            val yearView = header!!.findViewById<TextView>(R.id.select_album_year)
            val year: String

            year = if (albumHeader.years.size == 1)
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
