/*
 * PlayerFragment.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.HeartRating
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.SessionResult
import androidx.navigation.Navigation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.audiofx.VisualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.subsonic.ShareHandler
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.AutoRepeatButton
import org.moire.ultrasonic.view.VisualizerView
import timber.log.Timber

/**
 * Contains the Music Player screen of Ultrasonic with playback controls and the playlist
 * TODO: Add timeline lister -> updateProgressBar().
 */
@Suppress("LargeClass", "TooManyFunctions", "MagicNumber")
class PlayerFragment :
    Fragment(),
    GestureDetector.OnGestureListener,
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    // Settings
    private var swipeDistance = 0
    private var swipeVelocity = 0
    private var jukeboxAvailable = false
    private var useFiveStarRating = false
    private var isEqualizerAvailable = false
    private var isVisualizerAvailable = false

    // Detectors & Callbacks
    private lateinit var gestureScanner: GestureDetector
    private lateinit var cancellationToken: CancellationToken
    private lateinit var dragTouchHelper: ItemTouchHelper

    // Data & Services
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val mediaPlayerController: MediaPlayerController by inject()
    private val shareHandler: ShareHandler by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private var currentPlaying: DownloadFile? = null
    private var currentSong: Track? = null
    private lateinit var viewManager: LinearLayoutManager
    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()
    private lateinit var executorService: ScheduledExecutorService
    private var ioScope = CoroutineScope(Dispatchers.IO)

    // Views and UI Elements
    private lateinit var visualizerViewLayout: LinearLayout
    private lateinit var visualizerView: VisualizerView
    private lateinit var playlistNameView: EditText
    private lateinit var starMenuItem: MenuItem
    private lateinit var fiveStar1ImageView: ImageView
    private lateinit var fiveStar2ImageView: ImageView
    private lateinit var fiveStar3ImageView: ImageView
    private lateinit var fiveStar4ImageView: ImageView
    private lateinit var fiveStar5ImageView: ImageView
    private lateinit var playlistFlipper: ViewFlipper
    private lateinit var emptyTextView: TextView
    private lateinit var songTitleTextView: TextView
    private lateinit var artistTextView: TextView
    private lateinit var albumTextView: TextView
    private lateinit var genreTextView: TextView
    private lateinit var bitrateFormatTextView: TextView
    private lateinit var albumArtImageView: ImageView
    private lateinit var playlistView: RecyclerView
    private lateinit var positionTextView: TextView
    private lateinit var downloadTrackTextView: TextView
    private lateinit var downloadTotalDurationTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var pauseButton: View
    private lateinit var stopButton: View
    private lateinit var playButton: View
    private lateinit var shuffleButton: View
    private lateinit var repeatButton: ImageView
    private lateinit var hollowStar: Drawable
    private lateinit var fullStar: Drawable
    private lateinit var progressBar: SeekBar

    internal val viewAdapter: BaseAdapter<Identifiable> by lazy {
        BaseAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.current_playing, container, false)
    }

    private fun findViews(view: View) {
        playlistFlipper = view.findViewById(R.id.current_playing_playlist_flipper)
        emptyTextView = view.findViewById(R.id.playlist_empty)
        songTitleTextView = view.findViewById(R.id.current_playing_song)
        artistTextView = view.findViewById(R.id.current_playing_artist)
        albumTextView = view.findViewById(R.id.current_playing_album)
        genreTextView = view.findViewById(R.id.current_playing_genre)
        bitrateFormatTextView = view.findViewById(R.id.current_playing_bitrate_format)
        albumArtImageView = view.findViewById(R.id.current_playing_album_art_image)
        positionTextView = view.findViewById(R.id.current_playing_position)
        downloadTrackTextView = view.findViewById(R.id.current_playing_track)
        downloadTotalDurationTextView = view.findViewById(R.id.current_total_duration)
        durationTextView = view.findViewById(R.id.current_playing_duration)
        progressBar = view.findViewById(R.id.current_playing_progress_bar)
        playlistView = view.findViewById(R.id.playlist_view)

        pauseButton = view.findViewById(R.id.button_pause)
        stopButton = view.findViewById(R.id.button_stop)
        playButton = view.findViewById(R.id.button_start)
        repeatButton = view.findViewById(R.id.button_repeat)
        visualizerViewLayout = view.findViewById(R.id.current_playing_visualizer_layout)
        fiveStar1ImageView = view.findViewById(R.id.song_five_star_1)
        fiveStar2ImageView = view.findViewById(R.id.song_five_star_2)
        fiveStar3ImageView = view.findViewById(R.id.song_five_star_3)
        fiveStar4ImageView = view.findViewById(R.id.song_five_star_4)
        fiveStar5ImageView = view.findViewById(R.id.song_five_star_5)
    }

    @Suppress("LongMethod")
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        setTitle(this, R.string.common_appname)
        val windowManager = requireActivity().windowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y
        setHasOptionsMenu(true)
        useFiveStarRating = Settings.useFiveStarRating
        swipeDistance = (width + height) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100
        swipeVelocity = swipeDistance
        gestureScanner = GestureDetector(context, this)

        findViews(view)
        val previousButton: AutoRepeatButton = view.findViewById(R.id.button_previous)
        val nextButton: AutoRepeatButton = view.findViewById(R.id.button_next)
        shuffleButton = view.findViewById(R.id.button_shuffle)
        updateShuffleButtonState(mediaPlayerController.isShufflePlayEnabled)
        updateRepeatButtonState(mediaPlayerController.repeatMode)

        val ratingLinearLayout = view.findViewById<LinearLayout>(R.id.song_rating)
        if (!useFiveStarRating) ratingLinearLayout.isVisible = false
        hollowStar = Util.getDrawableFromAttribute(view.context, R.attr.star_hollow)
        fullStar = Util.getDrawableFromAttribute(view.context, R.attr.star_full)

        fiveStar1ImageView.setOnClickListener { setSongRating(1) }
        fiveStar2ImageView.setOnClickListener { setSongRating(2) }
        fiveStar3ImageView.setOnClickListener { setSongRating(3) }
        fiveStar4ImageView.setOnClickListener { setSongRating(4) }
        fiveStar5ImageView.setOnClickListener { setSongRating(5) }

        albumArtImageView.setOnTouchListener { _, me ->
            gestureScanner.onTouchEvent(me)
        }

        albumArtImageView.setOnClickListener {
            toggleFullScreenAlbumArt()
        }

        previousButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.previous()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        previousButton.setOnRepeatListener {
            val incrementTime = Settings.incrementTime
            changeProgress(-incrementTime)
        }

        nextButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.next()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        nextButton.setOnRepeatListener {
            val incrementTime = Settings.incrementTime
            changeProgress(incrementTime)
        }

        pauseButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.pause()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        stopButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.reset()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        playButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerController.play()
                onCurrentChanged()
                onSliderProgressChanged()
            }
        }

        shuffleButton.setOnClickListener {
            toggleShuffle()
        }

        repeatButton.setOnClickListener {
            var newRepeat = mediaPlayerController.repeatMode + 1
            if (newRepeat == 3) {
                newRepeat = 0
            }

            mediaPlayerController.repeatMode = newRepeat

            onPlaylistChanged()

            when (newRepeat) {
                0 -> Util.toast(
                    context, R.string.download_repeat_off
                )
                1 -> Util.toast(
                    context, R.string.download_repeat_single
                )
                2 -> Util.toast(
                    context, R.string.download_repeat_all
                )
                else -> {
                }
            }
        }

        progressBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                launch(CommunicationError.getHandler(context)) {
                    mediaPlayerController.seekTo(progressBar.progress)
                    onSliderProgressChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        initPlaylistDisplay()

        registerForContextMenu(playlistView)

        if (arguments != null && requireArguments().getBoolean(
                Constants.INTENT_SHUFFLE,
                false
            )
        ) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.isShufflePlayEnabled = true
        }

        visualizerViewLayout.isVisible = false
        VisualizerController.get().observe(
            requireActivity()
        ) { visualizerController ->
            if (visualizerController != null) {
                Timber.d("VisualizerController Observer.onChanged received controller")
                visualizerView = VisualizerView(context)
                visualizerViewLayout.addView(
                    visualizerView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                )

                visualizerViewLayout.isVisible = visualizerView.isActive

                visualizerView.setOnTouchListener { _, _ ->
                    visualizerView.isActive = !visualizerView.isActive
                    mediaPlayerController.showVisualization = visualizerView.isActive
                    true
                }
                isVisualizerAvailable = true
            } else {
                Timber.d("VisualizerController Observer.onChanged has no controller")
                visualizerViewLayout.isVisible = false
                isVisualizerAvailable = false
            }
        }

        EqualizerController.get().observe(
            requireActivity()
        ) { equalizerController ->
            isEqualizerAvailable = if (equalizerController != null) {
                Timber.d("EqualizerController Observer.onChanged received controller")
                true
            } else {
                Timber.d("EqualizerController Observer.onChanged has no controller")
                false
            }
        }

        // Observe playlist changes and update the UI
        rxBusSubscription += RxBus.playlistObservable.subscribe {
            // Use launch to ensure running it in the main thread
            launch {
                onPlaylistChanged()
            }
        }

        rxBusSubscription += RxBus.playerStateObservable.subscribe {
            // Use launch to ensure running it in the main thread
            launch {
                update()
            }
        }

        mediaPlayerController.controller?.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                onSliderProgressChanged()
            }
        })

        // Query the Jukebox state in an IO Context
        ioScope.launch(CommunicationError.getHandler(context)) {
            try {
                jukeboxAvailable = mediaPlayerController.isJukeboxAvailable
            } catch (all: Exception) {
                Timber.e(all)
            }
        }

        view.setOnTouchListener { _, event -> gestureScanner.onTouchEvent(event) }
    }

    private fun updateShuffleButtonState(isEnabled: Boolean) {
        if (isEnabled) {
            shuffleButton.alpha = ALPHA_ACTIVATED
        } else {
            shuffleButton.alpha = ALPHA_DEACTIVATED
        }
    }

    private fun updateRepeatButtonState(repeatMode: Int) {
        when (repeatMode) {
            0 -> {
                repeatButton.setImageDrawable(
                    Util.getDrawableFromAttribute(
                        requireContext(), R.attr.media_repeat_off
                    )
                )
                repeatButton.alpha = ALPHA_DEACTIVATED
            }
            1 -> {
                repeatButton.setImageDrawable(
                    Util.getDrawableFromAttribute(
                        requireContext(), R.attr.media_repeat_single
                    )
                )
                repeatButton.alpha = ALPHA_ACTIVATED
            }
            2 -> {
                repeatButton.setImageDrawable(
                    Util.getDrawableFromAttribute(
                        requireContext(), R.attr.media_repeat_all
                    )
                )
                repeatButton.alpha = ALPHA_ACTIVATED
            }
            else -> {
            }
        }
    }

    private fun toggleShuffle() {
        val isEnabled = mediaPlayerController.toggleShuffle()

        if (isEnabled) {
            Util.toast(activity, R.string.download_menu_shuffle_on)
        } else {
            Util.toast(activity, R.string.download_menu_shuffle_off)
        }

        updateShuffleButtonState(isEnabled)
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayerController.currentPlayingLegacy == null) {
            playlistFlipper.displayedChild = 1
        } else {
            // Download list and Album art must be updated when resumed
            onPlaylistChanged()
            onCurrentChanged()
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable { handler.post { update(cancellationToken) } }
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleWithFixedDelay(runnable, 0L, 500L, TimeUnit.MILLISECONDS)

        if (mediaPlayerController.keepScreenOn) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (::visualizerView.isInitialized) {
            visualizerView.isActive = mediaPlayerController.showVisualization
        }

        requireActivity().invalidateOptionsMenu()
    }

    // Scroll to current playing.
    private fun scrollToCurrent() {
        val index = mediaPlayerController.currentMediaItemIndex

        if (index != -1) {
            val smoothScroller = LinearSmoothScroller(context)
            smoothScroller.targetPosition = index
            viewManager.startSmoothScroll(smoothScroller)
        }
    }

    override fun onPause() {
        super.onPause()
        executorService.shutdown()
        if (::visualizerView.isInitialized) {
            visualizerView.isActive = mediaPlayerController.showVisualization
        }
    }

    override fun onDestroyView() {
        rxBusSubscription.dispose()
        cancel("CoroutineScope cancelled because the view was destroyed")
        cancellationToken.cancel()
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.nowplaying, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val screenOption = menu.findItem(R.id.menu_item_screen_on_off)
        val jukeboxOption = menu.findItem(R.id.menu_item_jukebox)
        val equalizerMenuItem = menu.findItem(R.id.menu_item_equalizer)
        val visualizerMenuItem = menu.findItem(R.id.menu_item_visualizer)
        val shareMenuItem = menu.findItem(R.id.menu_item_share)
        val shareSongMenuItem = menu.findItem(R.id.menu_item_share_song)
        starMenuItem = menu.findItem(R.id.menu_item_star)
        val bookmarkMenuItem = menu.findItem(R.id.menu_item_bookmark_set)
        val bookmarkRemoveMenuItem = menu.findItem(R.id.menu_item_bookmark_delete)

        if (isOffline()) {
            if (shareMenuItem != null) {
                shareMenuItem.isVisible = false
            }
            starMenuItem.isVisible = false
            if (bookmarkMenuItem != null) {
                bookmarkMenuItem.isVisible = false
            }
            if (bookmarkRemoveMenuItem != null) {
                bookmarkRemoveMenuItem.isVisible = false
            }
        }
        if (equalizerMenuItem != null) {
            equalizerMenuItem.isEnabled = isEqualizerAvailable
            equalizerMenuItem.isVisible = isEqualizerAvailable
        }
        if (visualizerMenuItem != null) {
            visualizerMenuItem.isEnabled = isVisualizerAvailable
            visualizerMenuItem.isVisible = isVisualizerAvailable
        }
        val mediaPlayerController = mediaPlayerController
        val downloadFile = mediaPlayerController.currentPlayingLegacy

        if (downloadFile != null) {
            currentSong = downloadFile.track
        }

        if (useFiveStarRating) starMenuItem.isVisible = false

        if (currentSong != null) {
            starMenuItem.icon = if (currentSong!!.starred) fullStar else hollowStar
            shareSongMenuItem.isVisible = true
        } else {
            starMenuItem.icon = hollowStar
            shareSongMenuItem.isVisible = false
        }

        if (mediaPlayerController.keepScreenOn) {
            screenOption?.setTitle(R.string.download_menu_screen_off)
        } else {
            screenOption?.setTitle(R.string.download_menu_screen_on)
        }

        if (jukeboxOption != null) {
            jukeboxOption.isEnabled = jukeboxAvailable
            jukeboxOption.isVisible = jukeboxAvailable
            if (mediaPlayerController.isJukeboxEnabled) {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_off)
            } else {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_on)
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, menuInfo)
        if (view === playlistView) {
            val info = menuInfo as AdapterContextMenuInfo?
            val downloadFile = viewAdapter.getCurrentList()[info!!.position] as DownloadFile
            val menuInflater = requireActivity().menuInflater
            menuInflater.inflate(R.menu.nowplaying_context, menu)
            val song: Track?

            song = downloadFile.track

            if (song.parent == null) {
                val menuItem = menu.findItem(R.id.menu_show_album)
                if (menuItem != null) {
                    menuItem.isVisible = false
                }
            }

            if (isOffline() || !Settings.shouldUseId3Tags) {
                menu.findItem(R.id.menu_show_artist)?.isVisible = false
            }

            if (isOffline()) {
                menu.findItem(R.id.menu_lyrics)?.isVisible = false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return menuItemSelected(item.itemId, null) || super.onOptionsItemSelected(item)
    }

    @Suppress("ComplexMethod", "LongMethod", "ReturnCount")
    private fun menuItemSelected(menuItemId: Int, song: DownloadFile?): Boolean {
        var track: Track? = null
        val bundle: Bundle
        if (song != null) {
            track = song.track
        }

        when (menuItemId) {
            R.id.menu_show_artist -> {
                if (track == null) return false

                if (Settings.shouldUseId3Tags) {
                    bundle = Bundle()
                    bundle.putString(Constants.INTENT_ID, track.artistId)
                    bundle.putString(Constants.INTENT_NAME, track.artist)
                    bundle.putString(Constants.INTENT_PARENT_ID, track.artistId)
                    bundle.putBoolean(Constants.INTENT_ARTIST, true)
                    Navigation.findNavController(requireView())
                        .navigate(R.id.playerToSelectAlbum, bundle)
                }
                return true
            }
            R.id.menu_show_album -> {
                if (track == null) return false

                val albumId = if (Settings.shouldUseId3Tags) track.albumId else track.parent
                bundle = Bundle()
                bundle.putString(Constants.INTENT_ID, albumId)
                bundle.putString(Constants.INTENT_NAME, track.album)
                bundle.putString(Constants.INTENT_PARENT_ID, track.parent)
                bundle.putBoolean(Constants.INTENT_IS_ALBUM, true)
                Navigation.findNavController(requireView())
                    .navigate(R.id.playerToSelectAlbum, bundle)
                return true
            }
            R.id.menu_lyrics -> {
                if (track == null) return false

                bundle = Bundle()
                bundle.putString(Constants.INTENT_ARTIST, track.artist)
                bundle.putString(Constants.INTENT_TITLE, track.title)
                Navigation.findNavController(requireView()).navigate(R.id.playerToLyrics, bundle)
                return true
            }
            R.id.menu_remove -> {
                onPlaylistChanged()
                return true
            }
            R.id.menu_item_screen_on_off -> {
                val window = requireActivity().window
                if (mediaPlayerController.keepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerController.keepScreenOn = false
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerController.keepScreenOn = true
                }
                return true
            }
            R.id.menu_shuffle -> {
                toggleShuffle()
                return true
            }
            R.id.menu_item_equalizer -> {
                Navigation.findNavController(requireView()).navigate(R.id.playerToEqualizer)
                return true
            }
            R.id.menu_item_visualizer -> {
                val active = !visualizerView.isActive
                visualizerView.isActive = active

                visualizerViewLayout.isVisible = visualizerView.isActive

                mediaPlayerController.showVisualization = visualizerView.isActive
                Util.toast(
                    context,
                    if (active) R.string.download_visualizer_on
                    else R.string.download_visualizer_off
                )
                return true
            }
            R.id.menu_item_jukebox -> {
                val jukeboxEnabled = !mediaPlayerController.isJukeboxEnabled
                mediaPlayerController.isJukeboxEnabled = jukeboxEnabled
                Util.toast(
                    context,
                    if (jukeboxEnabled) R.string.download_jukebox_on
                    else R.string.download_jukebox_off,
                    false
                )
                return true
            }
            R.id.menu_item_toggle_list -> {
                toggleFullScreenAlbumArt()
                return true
            }
            R.id.menu_item_clear_playlist -> {
                mediaPlayerController.isShufflePlayEnabled = false
                mediaPlayerController.clear()
                onPlaylistChanged()
                return true
            }
            R.id.menu_item_save_playlist -> {
                if (mediaPlayerController.playlistSize > 0) {
                    showSavePlaylistDialog()
                }
                return true
            }
            R.id.menu_item_star -> {
                if (currentSong == null) return true

                val isStarred = currentSong!!.starred
                val id = currentSong!!.id

                mediaPlayerController.controller?.setRating(
                    id,
                    HeartRating(!isStarred)
                )?.let {
                    Futures.addCallback(it, object : FutureCallback<SessionResult> {
                        override fun onSuccess(result: SessionResult?) {
                            if (isStarred) {
                                starMenuItem.icon = hollowStar
                                currentSong!!.starred = false
                            } else {
                                starMenuItem.icon = fullStar
                                currentSong!!.starred = true
                            }
                        }

                        override fun onFailure(t: Throwable) {
                            Toast.makeText(context, "SetRating failed", Toast.LENGTH_SHORT).show()
                        }
                    }, this.executorService)
                }

                return true
            }
            R.id.menu_item_bookmark_set -> {
                if (currentSong == null) return true

                val songId = currentSong!!.id
                val playerPosition = mediaPlayerController.playerPosition
                currentSong!!.bookmarkPosition = playerPosition
                val bookmarkTime = Util.formatTotalDuration(playerPosition.toLong(), true)
                Thread {
                    val musicService = getMusicService()
                    try {
                        musicService.createBookmark(songId, playerPosition)
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
                val msg = resources.getString(
                    R.string.download_bookmark_set_at_position,
                    bookmarkTime
                )
                Util.toast(context, msg)
                return true
            }
            R.id.menu_item_bookmark_delete -> {
                if (currentSong == null) return true

                val bookmarkSongId = currentSong!!.id
                currentSong!!.bookmarkPosition = 0
                Thread {
                    val musicService = getMusicService()
                    try {
                        musicService.deleteBookmark(bookmarkSongId)
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
                Util.toast(context, R.string.download_bookmark_removed)
                return true
            }
            R.id.menu_item_share -> {
                val mediaPlayerController = mediaPlayerController
                val tracks: MutableList<Track?> = ArrayList()
                val downloadServiceSongs = mediaPlayerController.playList
                for (downloadFile in downloadServiceSongs) {
                    val playlistEntry = downloadFile.track
                    tracks.add(playlistEntry)
                }
                shareHandler.createShare(this, tracks, null, cancellationToken)
                return true
            }
            R.id.menu_item_share_song -> {
                if (currentSong == null) return true

                val tracks: MutableList<Track?> = ArrayList()
                tracks.add(currentSong)

                shareHandler.createShare(this, tracks, null, cancellationToken)
                return true
            }
            else -> return false
        }
    }

    private fun update(cancel: CancellationToken? = null) {
        if (cancel?.isCancellationRequested == true) return
        val mediaPlayerController = mediaPlayerController
        if (currentPlaying != mediaPlayerController.currentPlayingLegacy) {
            onCurrentChanged()
        }
        onSliderProgressChanged()
        requireActivity().invalidateOptionsMenu()
    }

    private fun savePlaylistInBackground(playlistName: String) {
        Util.toast(context, resources.getString(R.string.download_playlist_saving, playlistName))
        mediaPlayerController.suggestedPlaylistName = playlistName

        ioScope.launch {

            val entries = mediaPlayerController.playList.map {
                it.track
            }
            val musicService = getMusicService()
            musicService.createPlaylist(null, playlistName, entries)
        }.invokeOnCompletion {
            if (it == null || it is CancellationException) {
                Util.toast(context, R.string.download_playlist_done)
            } else {
                Timber.e(it, "Exception has occurred in savePlaylistInBackground")
                val msg = String.format(
                    Locale.ROOT,
                    "%s %s",
                    resources.getString(R.string.download_playlist_error),
                    CommunicationError.getErrorMessage(it, context)
                )
                Util.toast(context, msg)
            }
        }
    }

    private fun toggleFullScreenAlbumArt() {
        if (playlistFlipper.displayedChild == 1) {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_out)
            playlistFlipper.displayedChild = 0
        } else {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_out)
            playlistFlipper.displayedChild = 1
        }
        scrollToCurrent()
    }

    private fun initPlaylistDisplay() {
        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        playlistView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        // Create listener
        val clickHandler: ((DownloadFile, Int) -> Unit) = { _, pos ->
            mediaPlayerController.seekTo(pos, 0)
            mediaPlayerController.prepare()
            mediaPlayerController.play()
            onCurrentChanged()
            onSliderProgressChanged()
        }

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = clickHandler,
                checkable = false,
                draggable = true,
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
            ).apply {
                this.startDrag = { holder ->
                    dragTouchHelper.startDrag(holder)
                }
            }
        )

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {

                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition

                // Move it in the data set
                mediaPlayerController.moveItemInPlaylist(from, to)
                return true
            }

            // Swipe to delete from playlist
            @SuppressLint("NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val item = mediaPlayerController.controller?.getMediaItemAt(pos)
                mediaPlayerController.removeFromPlaylist(pos)

                val songRemoved = String.format(
                    resources.getString(R.string.download_song_removed),
                    item?.mediaMetadata?.title
                )

                Util.toast(context, songRemoved)
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = ALPHA_DEACTIVATED
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        }

        dragTouchHelper = ItemTouchHelper(callback)

        dragTouchHelper.attachToRecyclerView(playlistView)
    }

    private fun onPlaylistChanged() {
        val mediaPlayerController = mediaPlayerController
        val list = mediaPlayerController.playList
        emptyTextView.setText(R.string.playlist_empty)

        viewAdapter.submitList(list)

        emptyTextView.isVisible = list.isEmpty()

        updateRepeatButtonState(mediaPlayerController.repeatMode)
    }

    private fun onCurrentChanged() {
        currentPlaying = mediaPlayerController.currentPlayingLegacy

        scrollToCurrent()
        val totalDuration = mediaPlayerController.playListDuration
        val totalSongs = mediaPlayerController.playlistSize.toLong()
        val currentSongIndex = mediaPlayerController.currentMediaItemIndex + 1
        val duration = Util.formatTotalDuration(totalDuration)
        val trackFormat =
            String.format(Locale.getDefault(), "%d / %d", currentSongIndex, totalSongs)
        if (currentPlaying != null) {
            currentSong = currentPlaying!!.track
            songTitleTextView.text = currentSong!!.title
            artistTextView.text = currentSong!!.artist
            albumTextView.text = currentSong!!.album
            if (currentSong!!.year != null && Settings.showNowPlayingDetails)
                albumTextView.append(String.format(Locale.ROOT, " (%d)", currentSong!!.year))

            if (Settings.showNowPlayingDetails) {
                genreTextView.text = currentSong!!.genre
                genreTextView.isVisible =
                    (currentSong!!.genre != null && currentSong!!.genre!!.isNotBlank())

                var bitRate = ""
                if (currentSong!!.bitRate != null && currentSong!!.bitRate!! > 0)
                    bitRate = String.format(
                        Util.appContext().getString(R.string.song_details_kbps),
                        currentSong!!.bitRate
                    )
                bitrateFormatTextView.text = String.format(
                    Locale.ROOT, "%s %s",
                    bitRate, currentSong!!.suffix
                )
                bitrateFormatTextView.isVisible = true
            } else {
                genreTextView.isVisible = false
                bitrateFormatTextView.isVisible = false
            }

            downloadTrackTextView.text = trackFormat
            downloadTotalDurationTextView.text = duration
            imageLoaderProvider.getImageLoader()
                .loadImage(albumArtImageView, currentSong, true, 0)
            displaySongRating()
        } else {
            currentSong = null
            songTitleTextView.text = null
            artistTextView.text = null
            albumTextView.text = null
            genreTextView.text = null
            bitrateFormatTextView.text = null
            downloadTrackTextView.text = null
            downloadTotalDurationTextView.text = null
            imageLoaderProvider.getImageLoader()
                .loadImage(albumArtImageView, null, true, 0)
        }
    }

    @Suppress("LongMethod")
    @Synchronized
    private fun onSliderProgressChanged() {

        val isJukeboxEnabled: Boolean = mediaPlayerController.isJukeboxEnabled
        val millisPlayed: Int = max(0, mediaPlayerController.playerPosition)
        val duration: Int = mediaPlayerController.playerDuration
        val playbackState: Int = mediaPlayerController.playbackState
        val isPlaying = mediaPlayerController.isPlaying

        if (cancellationToken.isCancellationRequested) return
        if (currentPlaying != null) {
            positionTextView.text = Util.formatTotalDuration(millisPlayed.toLong(), true)
            durationTextView.text = Util.formatTotalDuration(duration.toLong(), true)
            progressBar.max =
                if (duration == 0) 100 else duration // Work-around for apparent bug.
            progressBar.progress = millisPlayed
            progressBar.isEnabled = mediaPlayerController.isPlaying || isJukeboxEnabled
        } else {
            positionTextView.setText(R.string.util_zero_time)
            durationTextView.setText(R.string.util_no_time)
            progressBar.progress = 0
            progressBar.max = 0
            progressBar.isEnabled = false
        }

        val progress = mediaPlayerController.bufferedPercentage

        when (playbackState) {
            Player.STATE_BUFFERING -> {

                val downloadStatus = resources.getString(
                    R.string.download_playerstate_loading
                )
                progressBar.secondaryProgress = progress
                setTitle(this@PlayerFragment, downloadStatus)
            }
            Player.STATE_READY -> {
                progressBar.secondaryProgress = progress
                if (mediaPlayerController.isShufflePlayEnabled) {
                    setTitle(
                        this@PlayerFragment,
                        R.string.download_playerstate_playing_shuffle
                    )
                } else {
                    setTitle(this@PlayerFragment, R.string.common_appname)
                }
            }
            Player.STATE_IDLE,
            Player.STATE_ENDED,
            -> {
            }
            else -> setTitle(this@PlayerFragment, R.string.common_appname)
        }

        when (playbackState) {
            Player.STATE_READY -> {
                pauseButton.isVisible = isPlaying
                stopButton.isVisible = false
                playButton.isVisible = !isPlaying
            }
            Player.STATE_BUFFERING -> {
                pauseButton.isVisible = false
                stopButton.isVisible = true
                playButton.isVisible = false
            }
            else -> {
                pauseButton.isVisible = false
                stopButton.isVisible = false
                playButton.isVisible = true
            }
        }

        // TODO: It would be a lot nicer if MediaPlayerController would send an event
        // when this is necessary instead of updating every time
        displaySongRating()
    }

    private fun changeProgress(ms: Int) {
        launch(CommunicationError.getHandler(context)) {
            val msPlayed: Int = max(0, mediaPlayerController.playerPosition)
            val duration = mediaPlayerController.playerDuration
            val seekTo = (msPlayed + ms).coerceAtMost(duration)
            mediaPlayerController.seekTo(seekTo)
            progressBar.progress = seekTo
        }
    }

    override fun onDown(me: MotionEvent): Boolean {
        return false
    }

    @Suppress("ReturnCount")
    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val e1X = e1.x
        val e2X = e2.x
        val e1Y = e1.y
        val e2Y = e2.y
        val absX = abs(velocityX)
        val absY = abs(velocityY)

        // Right to Left swipe
        if (e1X - e2X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.next()
            onCurrentChanged()
            onSliderProgressChanged()
            return true
        }

        // Left to Right swipe
        if (e2X - e1X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.previous()
            onCurrentChanged()
            onSliderProgressChanged()
            return true
        }

        // Top to Bottom swipe
        if (e2Y - e1Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.seekTo(mediaPlayerController.playerPosition + 30000)
            onSliderProgressChanged()
            return true
        }

        // Bottom to Top swipe
        if (e1Y - e2Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerController.seekTo(mediaPlayerController.playerPosition - 8000)
            onSliderProgressChanged()
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    private fun displaySongRating() {
        var rating = 0

        if (currentSong?.userRating != null) {
            rating = currentSong!!.userRating!!
        }

        fiveStar1ImageView.setImageDrawable(if (rating > 0) fullStar else hollowStar)
        fiveStar2ImageView.setImageDrawable(if (rating > 1) fullStar else hollowStar)
        fiveStar3ImageView.setImageDrawable(if (rating > 2) fullStar else hollowStar)
        fiveStar4ImageView.setImageDrawable(if (rating > 3) fullStar else hollowStar)
        fiveStar5ImageView.setImageDrawable(if (rating > 4) fullStar else hollowStar)
    }

    private fun setSongRating(rating: Int) {
        if (currentSong == null) return
        displaySongRating()
        mediaPlayerController.setSongRating(rating)
    }

    private fun showSavePlaylistDialog() {
        val layout = LayoutInflater.from(this.context).inflate(R.layout.save_playlist, null)

        playlistNameView = layout.findViewById(R.id.save_playlist_name)

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.download_playlist_title)
        builder.setMessage(R.string.download_playlist_name)

        builder.setPositiveButton(R.string.common_save) { _, _ ->
            savePlaylistInBackground(
                playlistNameView.text.toString()
            )
        }

        builder.setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog.cancel() }
        builder.setView(layout)
        builder.setCancelable(true)
        val dialog = builder.create()
        val playlistName = mediaPlayerController.suggestedPlaylistName
        if (playlistName != null) {
            playlistNameView.setText(playlistName)
        } else {
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            playlistNameView.setText(dateFormat.format(Date()))
        }
        dialog.show()
    }

    companion object {
        private const val PERCENTAGE_OF_SCREEN_FOR_SWIPE = 5
        private const val ALPHA_ACTIVATED = 1f
        private const val ALPHA_DEACTIVATED = 0.4f
    }
}
