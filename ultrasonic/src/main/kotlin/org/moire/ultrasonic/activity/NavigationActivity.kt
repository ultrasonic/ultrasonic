/*
 * NavigationActivity.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.activity

import android.app.SearchManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.media.AudioManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.SearchRecentSuggestions
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentContainerView
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSettingDao
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.fragment.OnBackPressedHandler
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.LocaleHelper
import org.moire.ultrasonic.util.ServerColor
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.UncaughtExceptionHandler
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * The main Activity of Ultrasonic which loads all other screens as Fragments
 */
@Suppress("TooManyFunctions")
class NavigationActivity : AppCompatActivity() {
    private var chatMenuItem: MenuItem? = null
    private var bookmarksMenuItem: MenuItem? = null
    private var sharesMenuItem: MenuItem? = null
    private var podcastsMenuItem: MenuItem? = null
    private var nowPlayingView: FragmentContainerView? = null
    private var nowPlayingHidden = false
    private var navigationView: NavigationView? = null
    private var drawerLayout: DrawerLayout? = null
    private var host: NavHostFragment? = null
    private var selectServerButton: MaterialButton? = null
    private var headerBackgroundImage: ImageView? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerController: MediaPlayerController by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverRepository: ServerSettingDao by inject()

    private var infoDialogDisplayed = false
    private var currentFragmentId: Int = 0
    private var cachedServerCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setUncaughtExceptionHandler()
        Util.applyTheme(this)

        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.navigation_activity)
        nowPlayingView = findViewById(R.id.now_playing_fragment)
        navigationView = findViewById(R.id.nav_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment? ?: return

        val navController = host!!.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.mainFragment,
                R.id.mediaLibraryFragment,
                R.id.searchFragment,
                R.id.playlistsFragment,
                R.id.downloadsFragment,
                R.id.sharesFragment,
                R.id.bookmarksFragment,
                R.id.chatFragment,
                R.id.podcastFragment,
                R.id.settingsFragment,
                R.id.aboutFragment,
                R.id.playerFragment
            ),
            drawerLayout
        )

        setupActionBar(navController, appBarConfiguration)

        setupNavigationMenu(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (ignored: Resources.NotFoundException) {
                destination.id.toString()
            }
            Timber.d("Navigated to $dest")

            currentFragmentId = destination.id
            // Handle the hiding of the NowPlaying fragment when the Player is active
            if (currentFragmentId == R.id.playerFragment) {
                hideNowPlaying()
            } else {
                if (!nowPlayingHidden) showNowPlaying()
            }

            // Hides menu items for Offline mode
            setMenuForServerCapabilities()
        }

        // Determine if this is a first run
        val showWelcomeScreen = Util.isFirstRun()

        // Migrate Feature storage if needed
        // TODO: Remove in December 2022
        if (!Settings.hasKey(Constants.PREFERENCES_KEY_USE_FIVE_STAR_RATING)) {
            Settings.migrateFeatureStorage()
        }

        loadSettings()

        // This is a first run with only the demo entry inside the database
        // We set the active server to the demo one and show the welcome dialog
        if (showWelcomeScreen) {
            showWelcomeDialog()
        }

        RxBus.dismissNowPlayingCommandObservable.subscribe {
            nowPlayingHidden = true
            hideNowPlaying()
        }

        rxBusSubscription += RxBus.playerStateObservable.subscribe {
            if (it.state === PlayerState.STARTED || it.state === PlayerState.PAUSED)
                showNowPlaying()
            else
                hideNowPlaying()
        }

        rxBusSubscription += RxBus.themeChangedEventObservable.subscribe {
            recreate()
        }

        rxBusSubscription += RxBus.activeServerChangeObservable.subscribe {
            updateNavigationHeaderForServer()
        }

        serverRepository.liveServerCount().observe(this) { count ->
            cachedServerCount = count ?: 0
            updateNavigationHeaderForServer()
        }
    }

    private fun updateNavigationHeaderForServer() {
        val activeServer = activeServerProvider.getActiveServer()

        if (cachedServerCount == 0)
            selectServerButton?.text = getString(R.string.main_setup_server, activeServer.name)
        else selectServerButton?.text = activeServer.name

        val foregroundColor = ServerColor.getForegroundColor(this, activeServer.color)
        val backgroundColor = ServerColor.getBackgroundColor(this, activeServer.color)

        if (activeServer.index == 0)
            selectServerButton?.icon =
                ContextCompat.getDrawable(this, R.drawable.ic_menu_screen_on_off_dark)
        else
            selectServerButton?.icon =
                ContextCompat.getDrawable(this, R.drawable.ic_menu_select_server_dark)

        selectServerButton?.iconTint = ColorStateList.valueOf(foregroundColor)
        selectServerButton?.setTextColor(foregroundColor)
        headerBackgroundImage?.setBackgroundColor(backgroundColor)
    }

    override fun onResume() {
        super.onResume()

        Storage.reset()
        setMenuForServerCapabilities()

        // Lifecycle support's constructor registers some event receivers so it should be created early
        lifecycleSupport.onCreate()

        if (!nowPlayingHidden) showNowPlaying()
        else hideNowPlaying()
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBusSubscription.dispose()
        imageLoaderProvider.clearImageLoader()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeAdjust = isVolumeDown || isVolumeUp
        val isJukebox = mediaPlayerController.isJukeboxEnabled
        if (isVolumeAdjust && isJukebox) {
            mediaPlayerController.adjustJukeboxVolume(isVolumeUp)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupNavigationMenu(navController: NavController) {
        navigationView?.setupWithNavController(navController)

        // The exit menu is handled here manually
        val exitItem: MenuItem? = navigationView?.menu?.findItem(R.id.menu_exit)
        exitItem?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_exit) {
                setResult(Constants.RESULT_CLOSE_ALL)
                mediaPlayerController.stopJukeboxService()
                finish()
                exit()
            }
            true
        }

        chatMenuItem = navigationView?.menu?.findItem(R.id.chatFragment)
        bookmarksMenuItem = navigationView?.menu?.findItem(R.id.bookmarksFragment)
        sharesMenuItem = navigationView?.menu?.findItem(R.id.sharesFragment)
        podcastsMenuItem = navigationView?.menu?.findItem(R.id.podcastFragment)
        selectServerButton =
            navigationView?.getHeaderView(0)?.findViewById(R.id.header_select_server)
        selectServerButton?.setOnClickListener {
            if (drawerLayout?.isDrawerVisible(GravityCompat.START) == true)
                this.drawerLayout?.closeDrawer(GravityCompat.START)
            navController.navigate(R.id.serverSelectorFragment)
        }
        headerBackgroundImage =
            navigationView?.getHeaderView(0)?.findViewById(R.id.img_header_bg)
    }

    private fun setupActionBar(navController: NavController, appBarConfig: AppBarConfiguration) {
        setupActionBarWithNavController(navController, appBarConfig)
    }

    override fun onBackPressed() {
        if (drawerLayout?.isDrawerVisible(GravityCompat.START) == true) {
            this.drawerLayout?.closeDrawer(GravityCompat.START)
        } else {
            val currentFragment = host!!.childFragmentManager.fragments.last()
            if (currentFragment is OnBackPressedHandler) currentFragment.onBackPressed()
            else super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val retValue = super.onCreateOptionsMenu(menu)
        if (navigationView == null) {
            menuInflater.inflate(R.menu.navigation, menu)
            return true
        }
        return retValue
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return item.onNavDestinationSelected(findNavController(R.id.nav_host_fragment)) ||
            super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val currentFragment = host!!.childFragmentManager.fragments.last()
        return if (currentFragment is OnBackPressedHandler) {
            currentFragment.onBackPressed()
            true
        } else {
            findNavController(R.id.nav_host_fragment).navigateUp(appBarConfiguration)
        }
    }

    // TODO Test if this works with external Intents
    // android.intent.action.SEARCH and android.media.action.MEDIA_PLAY_FROM_SEARCH calls here
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        if (intent.getBooleanExtra(Constants.INTENT_SHOW_PLAYER, false)) {
            findNavController(R.id.nav_host_fragment).navigate(R.id.playerFragment)
            return
        }

        val query = intent.getStringExtra(SearchManager.QUERY)

        if (query != null) {
            val autoPlay = intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
            val suggestions = SearchRecentSuggestions(
                this,
                SearchSuggestionProvider.AUTHORITY, SearchSuggestionProvider.MODE
            )
            suggestions.saveRecentQuery(query, null)

            val bundle = Bundle()
            bundle.putString(Constants.INTENT_QUERY, query)
            bundle.putBoolean(Constants.INTENT_AUTOPLAY, autoPlay)
            findNavController(R.id.nav_host_fragment).navigate(R.id.searchFragment, bundle)
        }
    }

    /**
     * Apply the customized language settings if needed
     */
    override fun attachBaseContext(newBase: Context?) {
        val locale = Settings.overrideLanguage
        val localeUpdatedContext: ContextWrapper = LocaleHelper.wrap(newBase, locale)
        super.attachBaseContext(localeUpdatedContext)
    }

    private fun loadSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)
    }

    private fun exit() {
        lifecycleSupport.onDestroy()
        finish()
    }

    private fun showWelcomeDialog() {
        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true

            InfoDialog.Builder(this)
                .setTitle(R.string.main_welcome_title)
                .setMessage(R.string.main_welcome_text_demo)
                .setNegativeButton(R.string.main_welcome_cancel) { dialog, _ ->
                    // Go to the settings screen
                    dialog.dismiss()
                    findNavController(R.id.nav_host_fragment).navigate(R.id.serverSelectorFragment)
                }
                .setPositiveButton(R.string.common_ok) { dialog, _ ->
                    // Add the demo server
                    val activeServerProvider: ActiveServerProvider by inject()
                    val demoIndex = serverSettingsModel.addDemoServer()
                    activeServerProvider.setActiveServerByIndex(demoIndex)
                    findNavController(R.id.nav_host_fragment).navigate(R.id.mainFragment)
                    dialog.dismiss()
                }.show()
        }
    }

    private fun setUncaughtExceptionHandler() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        if (handler !is UncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        }
    }

    private fun showNowPlaying() {
        if (!Settings.showNowPlaying) {
            hideNowPlaying()
            return
        }

        // The logic for nowPlayingHidden is that the user can dismiss NowPlaying with a gesture,
        // and when the MediaPlayerService requests that it should be shown, it returns
        nowPlayingHidden = false
        // Do not show for Player fragment
        if (currentFragmentId == R.id.playerFragment) {
            hideNowPlaying()
            return
        }

        if (nowPlayingView != null) {
            val playerState: Int = mediaPlayerController.playbackState
            if (playerState == STATE_BUFFERING || playerState == STATE_READY) {
                val file: DownloadFile? = mediaPlayerController.currentPlayingLegacy
                if (file != null) {
                    nowPlayingView?.visibility = View.VISIBLE
                }
            } else {
                hideNowPlaying()
            }
        }
    }

    private fun hideNowPlaying() {
        nowPlayingView?.visibility = View.GONE
    }

    private fun setMenuForServerCapabilities() {
        if (ActiveServerProvider.isOffline()) {
            chatMenuItem?.isVisible = false
            bookmarksMenuItem?.isVisible = false
            sharesMenuItem?.isVisible = false
            podcastsMenuItem?.isVisible = false
            return
        }
        val activeServer = activeServerProvider.getActiveServer()
        chatMenuItem?.isVisible = activeServer.chatSupport != false
        bookmarksMenuItem?.isVisible = activeServer.bookmarkSupport != false
        sharesMenuItem?.isVisible = activeServer.shareSupport != false
        podcastsMenuItem?.isVisible = activeServer.podcastSupport != false
    }
}
