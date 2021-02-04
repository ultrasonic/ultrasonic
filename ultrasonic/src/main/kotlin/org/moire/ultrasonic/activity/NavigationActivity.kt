package org.moire.ultrasonic.activity

import android.app.AlertDialog
import android.content.res.Resources
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber


/**
 * A simple activity demonstrating use of a NavHostFragment with a navigation drawer.
 */
class NavigationActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration : AppBarConfiguration
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerController: MediaPlayerController by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    private var infoDialogDisplayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.navigation_activity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val host: NavHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment? ?: return

        val navController = host.navController

        val drawerLayout : DrawerLayout? = findViewById(R.id.drawer_layout)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.mainFragment, R.id.selectArtistFragment, R.id.searchFragment,
                R.id.playlistsFragment, R.id.sharesFragment, R.id.bookmarksFragment,
                R.id.chatFragment, R.id.podcastFragment, R.id.settingsFragment,
                R.id.aboutFragment),
            drawerLayout)

        setupActionBar(navController, appBarConfiguration)

        setupNavigationMenu(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (e: Resources.NotFoundException) {
                Integer.toString(destination.id)
            }
            Timber.d("Navigated to $dest")
        }

        // Determine first run and migrate server settings to DB as early as possible
        var showWelcomeScreen = Util.isFirstRun(this)
        val areServersMigrated: Boolean = serverSettingsModel.migrateFromPreferences()

        // If there are any servers in the DB, do not show the welcome screen
        showWelcomeScreen = showWelcomeScreen and !areServersMigrated

        loadSettings()
        showInfoDialog(showWelcomeScreen)
    }

    private fun setupNavigationMenu(navController: NavController) {
        val sideNavView = findViewById<NavigationView>(R.id.nav_view)
        sideNavView?.setupWithNavController(navController)

        // The exit menu is handled here manually
        val exitItem: MenuItem = sideNavView.menu.findItem(R.id.menu_exit)
        exitItem.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_exit) {
                setResult(Constants.RESULT_CLOSE_ALL)
                mediaPlayerController.stopJukeboxService()
                imageLoaderProvider.getImageLoader().stopImageLoader()
                finish()
                exit()
            }
            true
        }
    }

    private fun setupActionBar(navController: NavController, appBarConfig: AppBarConfiguration) {
        setupActionBarWithNavController(navController, appBarConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val retValue = super.onCreateOptionsMenu(menu)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        if (navigationView == null) {
            menuInflater.inflate(R.menu.navigation, menu)
            return true
        }
        return retValue
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return item.onNavDestinationSelected(findNavController(R.id.nav_host_fragment))
                || super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp(appBarConfiguration)
    }

    private fun loadSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)
        val preferences = Util.getPreferences(this)
        if (!preferences.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION)) {
            val editor = preferences.edit()
            editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, FileUtil.getDefaultMusicDirectory(this).path)
            editor.apply()
        }
    }

    private fun exit() {
        lifecycleSupport.onDestroy()
        Util.unregisterMediaButtonEventReceiver(this, false)
        finish()
    }

    private fun showInfoDialog(show: Boolean) {
        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true
            if (show) {
                AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.main_welcome_title)
                    .setMessage(R.string.main_welcome_text)
                    .setPositiveButton(R.string.common_ok) { dialog, i ->
                        dialog.dismiss()
                        findNavController(R.id.nav_host_fragment).navigate(R.id.settingsFragment)
                    }.show()
            }
        }
    }
}
