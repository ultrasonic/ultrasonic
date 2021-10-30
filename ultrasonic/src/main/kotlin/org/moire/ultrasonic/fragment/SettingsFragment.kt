package org.moire.ultrasonic.fragment

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.featureflags.Feature
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.filepicker.FilePickerDialog.Companion.createFilePickerDialog
import org.moire.ultrasonic.filepicker.OnFileSelectedListener
import org.moire.ultrasonic.fragment.FragmentTitle.Companion.setTitle
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.FileLoggerTree.Companion.deleteLogFiles
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileNumber
import org.moire.ultrasonic.log.FileLoggerTree.Companion.getLogFileSizes
import org.moire.ultrasonic.log.FileLoggerTree.Companion.plantToTimberForest
import org.moire.ultrasonic.log.FileLoggerTree.Companion.uprootFromTimberForest
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil.defaultMusicDirectory
import org.moire.ultrasonic.util.FileUtil.ensureDirectoryExistsAndIsReadWritable
import org.moire.ultrasonic.util.FileUtil.ultrasonicDirectory
import org.moire.ultrasonic.util.MediaSessionHandler
import org.moire.ultrasonic.util.PermissionUtil
import org.moire.ultrasonic.util.PermissionUtil.Companion.requestInitialPermission
import org.moire.ultrasonic.util.PermissionUtil.PermissionRequestFinishedCallback
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Settings.preferences
import org.moire.ultrasonic.util.Settings.shareGreeting
import org.moire.ultrasonic.util.Settings.shouldUseId3Tags
import org.moire.ultrasonic.util.ThemeChangedEventDistributor
import org.moire.ultrasonic.util.TimeSpanPreference
import org.moire.ultrasonic.util.TimeSpanPreferenceDialogFragmentCompat
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber
import java.io.File
import kotlin.math.ceil

/**
 * Shows main app settings.
 */
class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener, KoinComponent {
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
    private var lockScreenEnabled: CheckBoxPreference? = null
    private var sendBluetoothNotifications: CheckBoxPreference? = null
    private var sendBluetoothAlbumArt: CheckBoxPreference? = null
    private var showArtistPicture: CheckBoxPreference? = null
    private var viewRefresh: ListPreference? = null
    private var sharingDefaultDescription: EditTextPreference? = null
    private var sharingDefaultGreeting: EditTextPreference? = null
    private var sharingDefaultExpiration: TimeSpanPreference? = null
    private var resumeOnBluetoothDevice: Preference? = null
    private var pauseOnBluetoothDevice: Preference? = null
    private var debugLogToFile: CheckBoxPreference? = null

    private val mediaPlayerControllerLazy = inject<MediaPlayerController>(
        MediaPlayerController::class.java
    )
    private val permissionUtil = inject<PermissionUtil>(
        PermissionUtil::class.java
    )
    private val themeChangedEventDistributor = inject<ThemeChangedEventDistributor>(
        ThemeChangedEventDistributor::class.java
    )
    private val mediaSessionHandler = inject<MediaSessionHandler>(
        MediaSessionHandler::class.java
    )

    override fun onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
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
        lockScreenEnabled = findPreference(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS)
        sendBluetoothAlbumArt = findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART)
        sendBluetoothNotifications =
            findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS)
        viewRefresh = findPreference(Constants.PREFERENCES_KEY_VIEW_REFRESH)
        sharingDefaultDescription =
            findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION)
        sharingDefaultGreeting = findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING)
        sharingDefaultExpiration =
            findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION)
        resumeOnBluetoothDevice =
            findPreference(Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE)
        pauseOnBluetoothDevice = findPreference(Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE)
        debugLogToFile = findPreference(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE)
        showArtistPicture = findPreference(Constants.PREFERENCES_KEY_SHOW_ARTIST_PICTURE)
        sharingDefaultGreeting!!.text = shareGreeting
        setupClearSearchPreference()
        setupFeatureFlagsPreferences()
        setupCacheLocationPreference()
        setupBluetoothDevicePreferences()

        // After API26 foreground services must be used for music playback, and they must have a notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationsCategory =
                findPreference<PreferenceCategory>(Constants.PREFERENCES_KEY_CATEGORY_NOTIFICATIONS)
            var preferenceToRemove =
                findPreference<Preference>(Constants.PREFERENCES_KEY_SHOW_NOTIFICATION)
            if (preferenceToRemove != null) notificationsCategory!!.removePreference(
                preferenceToRemove
            )
            preferenceToRemove = findPreference(Constants.PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION)
            if (preferenceToRemove != null) notificationsCategory!!.removePreference(
                preferenceToRemove
            )
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        update()
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
            Constants.PREFERENCES_KEY_MEDIA_BUTTONS -> {
                setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true))
            }
            Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS -> {
                setBluetoothPreferences(sharedPreferences.getBoolean(key, true))
            }
            Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE -> {
                setDebugLogToFile(sharedPreferences.getBoolean(key, false))
            }
            Constants.PREFERENCES_KEY_ID3_TAGS -> {
                showArtistPicture!!.isEnabled = sharedPreferences.getBoolean(key, false)
            }
            Constants.PREFERENCES_KEY_THEME -> {
                themeChangedEventDistributor.value.RaiseThemeChangedEvent()
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
        cacheLocation!!.summary = Settings.cacheLocation
        cacheLocation!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                // If the user tries to change the cache location, we must first check to see if we have write access.
                requestInitialPermission(
                    requireActivity(),
                    object : PermissionRequestFinishedCallback {
                        override fun onPermissionRequestFinished(hasPermission: Boolean) {
                            if (hasPermission) {
                                val filePickerDialog = createFilePickerDialog(
                                    context!!
                                )
                                filePickerDialog.setDefaultDirectory(defaultMusicDirectory.path)
                                filePickerDialog.setInitialDirectory(cacheLocation!!.summary.toString())
                                filePickerDialog.setOnFileSelectedListener(object :
                                    OnFileSelectedListener {
                                    override fun onFileSelected(file: File?, path: String?) {
                                        if (path != null) {
                                            Settings.cacheLocation = path
                                            setCacheLocation(path)
                                        }
                                    }
                                })
                                filePickerDialog.show()
                            }
                        }
                    })
                true
            }
    }

    private fun setupBluetoothDevicePreferences() {
        val resumeSetting = Settings.resumeOnBluetoothDevice
        val pauseSetting = Settings.pauseOnBluetoothDevice
        resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(resumeSetting)
        pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(pauseSetting)
        resumeOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_resume_on_bluetooth_device,
                    Settings.resumeOnBluetoothDevice
                ) { choice: Int ->
                    val editor = resumeOnBluetoothDevice!!.sharedPreferences.edit()
                    editor.putInt(Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE, choice)
                    editor.apply()
                    resumeOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
        pauseOnBluetoothDevice!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showBluetoothDevicePreferenceDialog(
                    R.string.settings_playback_pause_on_bluetooth_device,
                    Settings.pauseOnBluetoothDevice
                ) { choice: Int ->
                    Settings.pauseOnBluetoothDevice = choice
                    pauseOnBluetoothDevice!!.summary = bluetoothDevicePreferenceToString(choice)
                }
                true
            }
    }

    private fun showBluetoothDevicePreferenceDialog(
        @StringRes title: Int,
        defaultChoice: Int,
        onChosen: (Int) -> Unit
    ) {
        val choice = intArrayOf(defaultChoice)
        AlertDialog.Builder(activity).setTitle(title)
            .setSingleChoiceItems(
                R.array.bluetoothDeviceSettingNames, defaultChoice
            ) { _: DialogInterface?, i: Int -> choice[0] = i }
            .setNegativeButton(R.string.common_cancel) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.cancel()
            }
            .setPositiveButton(R.string.common_ok) { dialogInterface: DialogInterface, _: Int ->
                onChosen(choice[0])
                dialogInterface.dismiss()
            }
            .create().show()
    }

    private fun bluetoothDevicePreferenceToString(preferenceValue: Int): String {
        return when (preferenceValue) {
            Constants.PREFERENCE_VALUE_ALL -> getString(R.string.settings_playback_bluetooth_all)
            Constants.PREFERENCE_VALUE_A2DP -> getString(R.string.settings_playback_bluetooth_a2dp)
            Constants.PREFERENCE_VALUE_DISABLED -> getString(R.string.settings_playback_bluetooth_disabled)
            else -> ""
        }
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

    private fun setupFeatureFlagsPreferences() {
        val featureStorage = get<FeatureStorage>(FeatureStorage::class.java)
        val useFiveStarRating = findPreference<Preference>(
            Constants.PREFERENCES_KEY_USE_FIVE_STAR_RATING
        ) as CheckBoxPreference?
        if (useFiveStarRating != null) {
            useFiveStarRating.isChecked = featureStorage.isFeatureEnabled(Feature.FIVE_STAR_RATING)
            useFiveStarRating.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, o: Any? ->
                    featureStorage.changeFeatureFlag(Feature.FIVE_STAR_RATING, (o as Boolean?)!!)
                    true
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
        viewRefresh!!.summary = viewRefresh!!.entry
        sharingDefaultExpiration!!.summary = sharingDefaultExpiration!!.text
        sharingDefaultDescription!!.summary = sharingDefaultDescription!!.text
        sharingDefaultGreeting!!.summary = sharingDefaultGreeting!!.text
        cacheLocation!!.summary = Settings.cacheLocation
        if (!mediaButtonsEnabled!!.isChecked) {
            lockScreenEnabled!!.isChecked = false
            lockScreenEnabled!!.isEnabled = false
        }
        if (!sendBluetoothNotifications!!.isChecked) {
            sendBluetoothAlbumArt!!.isChecked = false
            sendBluetoothAlbumArt!!.isEnabled = false
        }
        if (debugLogToFile!!.isChecked) {
            debugLogToFile!!.summary = getString(
                R.string.settings_debug_log_path,
                ultrasonicDirectory, FileLoggerTree.FILENAME
            )
        } else {
            debugLogToFile!!.summary = ""
        }
        showArtistPicture!!.isEnabled = shouldUseId3Tags
    }

    private fun setHideMedia(hide: Boolean) {
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

    private fun setMediaButtonsEnabled(enabled: Boolean) {
        lockScreenEnabled!!.isEnabled = enabled
        mediaSessionHandler.value.updateMediaButtonReceiver()
    }

    private fun setBluetoothPreferences(enabled: Boolean) {
        sendBluetoothAlbumArt!!.isEnabled = enabled
    }

    private fun setCacheLocation(path: String) {
        val dir = File(path)
        if (!ensureDirectoryExistsAndIsReadWritable(dir)) {
            permissionUtil.value.handlePermissionFailed(object : PermissionRequestFinishedCallback {
                override fun onPermissionRequestFinished(hasPermission: Boolean) {
                    val currentPath = Settings.cacheLocation
                    cacheLocation!!.summary = currentPath
                }
            })
        } else {
            cacheLocation!!.summary = path
        }

        // Clear download queue.
        mediaPlayerControllerLazy.value.clear()
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
                ceil(fileSize / 1000 * 1000.0).toString(),
                ultrasonicDirectory
            )
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setNegativeButton(R.string.settings_debug_log_keep) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.cancel()
                }
                .setPositiveButton(R.string.settings_debug_log_delete) { dialogInterface: DialogInterface, _: Int ->
                    deleteLogFiles()
                    Timber.i("Deleted debug log files")
                    dialogInterface.dismiss()
                    AlertDialog.Builder(activity).setMessage(R.string.settings_debug_log_deleted)
                        .setPositiveButton(R.string.common_ok) { dialogInterface1: DialogInterface, _: Int ->
                            dialogInterface1.dismiss()
                        }
                        .create().show()
                }
                .create().show()
        }
    }
}
