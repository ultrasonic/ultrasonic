package org.moire.ultrasonic.fragment

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.SearchRecentSuggestions
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import kotlin.math.ceil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.FileLoggerTree.Companion.deleteLogFiles
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileNumber
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileSizes
import org.moire.ultrasonic.log.FileLoggerTree.Companion.plantToTimberForest
import org.moire.ultrasonic.log.FileLoggerTree.Companion.uprootFromTimberForest
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.FileUtil.ultrasonicDirectory
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Settings.preferences
import org.moire.ultrasonic.util.Settings.shareGreeting
import org.moire.ultrasonic.util.Settings.shouldUseId3Tags
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.TimeSpanPreference
import org.moire.ultrasonic.util.TimeSpanPreferenceDialogFragmentCompat
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

/**
 * Shows main app settings.
 */
@Suppress("TooManyFunctions")
class SettingsFragment :
    PreferenceFragmentCompat(),
    OnSharedPreferenceChangeListener,
    KoinComponent {
    private var theme: ListPreference? = null
    private var maxBitrateWifi: ListPreference? = null
    private var maxBitrateMobile: ListPreference? = null
    private var cacheSize: ListPreference? = null
    private var cacheLocation: Preference? = null
    private var preloadCount: ListPreference? = null
    private var bufferLength: ListPreference? = null
    private var incrementTime: ListPreference? = null
    private var networkTimeout: ListPreference? = null
    private var maxAlbums: ListPreference? = null
    private var maxSongs: ListPreference? = null
    private var maxArtists: ListPreference? = null
    private var defaultAlbums: ListPreference? = null
    private var defaultSongs: ListPreference? = null
    private var defaultArtists: ListPreference? = null
    private var chatRefreshInterval: ListPreference? = null
    private var directoryCacheTime: ListPreference? = null
    private var mediaButtonsEnabled: CheckBoxPreference? = null
    private var showArtistPicture: CheckBoxPreference? = null
    private var sharingDefaultDescription: EditTextPreference? = null
    private var sharingDefaultGreeting: EditTextPreference? = null
    private var sharingDefaultExpiration: TimeSpanPreference? = null
    private var debugLogToFile: CheckBoxPreference? = null
    private var customCacheLocation: CheckBoxPreference? = null

    private val mediaPlayerController: MediaPlayerController by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.menu_settings)
        theme = findPreference(Constants.PREFERENCES_KEY_THEME)
        maxBitrateWifi = findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI)
        maxBitrateMobile = findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE)
        cacheSize = findPreference(Constants.PREFERENCES_KEY_CACHE_SIZE)
        cacheLocation = findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION)
        preloadCount = findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT)
        bufferLength = findPreference(Constants.PREFERENCES_KEY_BUFFER_LENGTH)
        incrementTime = findPreference(Constants.PREFERENCES_KEY_INCREMENT_TIME)
        networkTimeout = findPreference(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT)
        maxAlbums = findPreference(Constants.PREFERENCES_KEY_MAX_ALBUMS)
        maxSongs = findPreference(Constants.PREFERENCES_KEY_MAX_SONGS)
        maxArtists = findPreference(Constants.PREFERENCES_KEY_MAX_ARTISTS)
        defaultArtists = findPreference(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS)
        defaultSongs = findPreference(Constants.PREFERENCES_KEY_DEFAULT_SONGS)
        defaultAlbums = findPreference(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS)
        chatRefreshInterval = findPreference(Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL)
        directoryCacheTime = findPreference(Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME)
        mediaButtonsEnabled = findPreference(Constants.PREFERENCES_KEY_MEDIA_BUTTONS)
        sharingDefaultDescription =
            findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION)
        sharingDefaultGreeting = findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING)
        sharingDefaultExpiration =
            findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION)
        debugLogToFile = findPreference(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE)
        showArtistPicture = findPreference(Constants.PREFERENCES_KEY_SHOW_ARTIST_PICTURE)
        customCacheLocation = findPreference(Constants.PREFERENCES_KEY_CUSTOM_CACHE_LOCATION)

        sharingDefaultGreeting?.text = shareGreeting
        setupClearSearchPreference()
        setupCacheLocationPreference()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        update()
    }

    /**
     * This function will be called when we return from the file picker
     * with a new custom cache location
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (
            requestCode == SELECT_CACHE_ACTIVITY &&
            resultCode == Activity.RESULT_OK &&
            resultData != null
        ) {
            val read = (resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
            val write = (resultData.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0
            val persist = (resultData.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0

            if (read && write && persist) {
                if (resultData.data != null) {
                    // The result data contains a URI for the document or directory that
                    // the user selected.
                    val uri = resultData.data!!
                    val contentResolver = UApp.applicationContext().contentResolver

                    contentResolver.takePersistableUriPermission(uri, RW_FLAG)
                    setCacheLocation(uri.toString())
                    setupCacheLocationPreference()
                    return
                }
            }
            ErrorDialog.Builder(context)
                .setMessage(R.string.settings_cache_location_error)
                .show()
        }

        if (Settings.cacheLocationUri == "") {
            Settings.customCacheLocation = false
            customCacheLocation?.isChecked = false
            setupCacheLocationPreference()
        }
    }

    override fun onResume() {
        super.onResume()
        val preferences = preferences
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        val prefs = preferences
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        Timber.d("Preference changed: %s", key)
        update()
        when (key) {
            Constants.PREFERENCES_KEY_HIDE_MEDIA -> {
                setHideMedia(sharedPreferences.getBoolean(key, false))
            }
            Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE -> {
                setDebugLogToFile(sharedPreferences.getBoolean(key, false))
            }
            Constants.PREFERENCES_KEY_ID3_TAGS -> {
                showArtistPicture!!.isEnabled = sharedPreferences.getBoolean(key, false)
            }
            Constants.PREFERENCES_KEY_THEME -> {
                RxBus.themeChangedEventPublisher.onNext(Unit)
            }
            Constants.PREFERENCES_KEY_CUSTOM_CACHE_LOCATION -> {
                if (Settings.customCacheLocation) {
                    selectCacheLocation()
                } else {
                    if (Settings.cacheLocationUri != "") setCacheLocation("")
                    setupCacheLocationPreference()
                }
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        var dialogFragment: DialogFragment? = null
        if (preference is TimeSpanPreference) {
            dialogFragment = TimeSpanPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString("key", preference.getKey())
            dialogFragment.setArguments(bundle)
        }
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                this.parentFragmentManager,
                "android.support.v7.preference.PreferenceFragment.DIALOG"
            )
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun setupCacheLocationPreference() {
        if (!Settings.customCacheLocation) {
            cacheLocation?.isVisible = false
            return
        }

        cacheLocation?.isVisible = true
        val uri = Uri.parse(Settings.cacheLocationUri)
        cacheLocation!!.summary = uri.path
        cacheLocation!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            selectCacheLocation()
            true
        }
    }

    private fun selectCacheLocation() {
        // Choose a directory using the system's file picker.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        if (Settings.cacheLocationUri != "" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Settings.cacheLocationUri)
        }

        intent.addFlags(RW_FLAG)
        intent.addFlags(PERSISTABLE_FLAG)

        startActivityForResult(intent, SELECT_CACHE_ACTIVITY)
    }

    private fun setupClearSearchPreference() {
        val clearSearchPreference =
            findPreference<Preference>(Constants.PREFERENCES_KEY_CLEAR_SEARCH_HISTORY)
        if (clearSearchPreference != null) {
            clearSearchPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val suggestions = SearchRecentSuggestions(
                        activity,
                        SearchSuggestionProvider.AUTHORITY,
                        SearchSuggestionProvider.MODE
                    )
                    suggestions.clearHistory()
                    toast(activity, R.string.settings_search_history_cleared)
                    false
                }
        }
    }

    private fun update() {
        theme!!.summary = theme!!.entry
        maxBitrateWifi!!.summary = maxBitrateWifi!!.entry
        maxBitrateMobile!!.summary = maxBitrateMobile!!.entry
        cacheSize!!.summary = cacheSize!!.entry
        preloadCount!!.summary = preloadCount!!.entry
        bufferLength!!.summary = bufferLength!!.entry
        incrementTime!!.summary = incrementTime!!.entry
        networkTimeout!!.summary = networkTimeout!!.entry
        maxAlbums!!.summary = maxAlbums!!.entry
        maxArtists!!.summary = maxArtists!!.entry
        maxSongs!!.summary = maxSongs!!.entry
        defaultAlbums!!.summary = defaultAlbums!!.entry
        defaultArtists!!.summary = defaultArtists!!.entry
        defaultSongs!!.summary = defaultSongs!!.entry
        chatRefreshInterval!!.summary = chatRefreshInterval!!.entry
        directoryCacheTime!!.summary = directoryCacheTime!!.entry
        sharingDefaultExpiration!!.summary = sharingDefaultExpiration!!.text
        sharingDefaultDescription!!.summary = sharingDefaultDescription!!.text
        sharingDefaultGreeting!!.summary = sharingDefaultGreeting!!.text

        if (debugLogToFile?.isChecked == true) {
            debugLogToFile?.summary = getString(
                R.string.settings_debug_log_path,
                ultrasonicDirectory, FileLoggerTree.FILENAME
            )
        } else {
            debugLogToFile?.summary = ""
        }
        showArtistPicture?.isEnabled = shouldUseId3Tags
    }

    private fun setHideMedia(hide: Boolean) {
        // TODO this only hides the media files in the Ultrasonic dir and not in the music cache
        val nomediaDir = File(ultrasonicDirectory, ".nomedia")
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Timber.w("Failed to create %s", nomediaDir)
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Timber.w("Failed to delete %s", nomediaDir)
            }
        }
        toast(activity, R.string.settings_hide_media_toast, false)
    }

    private fun setCacheLocation(path: String) {
        if (path != "") {
            val uri = Uri.parse(path)
            cacheLocation!!.summary = uri.path ?: ""
        }

        Settings.cacheLocationUri = path

        // Clear download queue.
        mediaPlayerController.clear()
        mediaPlayerController.clearCaches()
        Storage.reset()
    }

    private fun setDebugLogToFile(writeLog: Boolean) {
        if (writeLog) {
            plantToTimberForest()
            Timber.i("Enabled debug logging to file")
        } else {
            uprootFromTimberForest()
            Timber.i("Disabled debug logging to file")
            val fileNum = getLogFileNumber()
            val fileSize = getLogFileSizes()
            val message = getString(
                R.string.settings_debug_log_summary,
                fileNum.toString(),
                ceil(fileSize.toDouble() / 1000 / 1000).toString(),
                ultrasonicDirectory
            )
            val keep = R.string.settings_debug_log_keep
            val delete = R.string.settings_debug_log_delete
            InfoDialog.Builder(activity)
                .setMessage(message)
                .setNegativeButton(keep) { dIf: DialogInterface, _: Int ->
                    dIf.cancel()
                }
                .setPositiveButton(delete) { dIf: DialogInterface, _: Int ->
                    deleteLogFiles()
                    Timber.i("Deleted debug log files")
                    dIf.dismiss()
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.settings_debug_log_deleted)
                        .setPositiveButton(R.string.common_ok) { dIf2: DialogInterface, _: Int ->
                            dIf2.dismiss()
                        }
                        .create().show()
                }
                .create().show()
        }
    }

    companion object {
        const val SELECT_CACHE_ACTIVITY = 161161
        const val RW_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val PERSISTABLE_FLAG = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
