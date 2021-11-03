/*
 * Settings.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.regex.Pattern
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.RepeatMode

/**
 * Contains convenience functions for reading and writing preferences
 */
object Settings {
    private val PATTERN = Pattern.compile(":")

    var repeatMode: RepeatMode
        get() {
            val preferences = preferences
            return RepeatMode.valueOf(
                preferences.getString(
                    Constants.PREFERENCES_KEY_REPEAT_MODE,
                    RepeatMode.OFF.name
                )!!
            )
        }
        set(repeatMode) {
            val preferences = preferences
            val editor = preferences.edit()
            editor.putString(Constants.PREFERENCES_KEY_REPEAT_MODE, repeatMode.name)
            editor.apply()
        }

    // After API26 foreground services must be used for music playback,
    // and they must have a notification
    val isNotificationEnabled: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return true
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOTIFICATION, false)
        }

    // After API26 foreground services must be used for music playback,
    // and they must have a notification
    val isNotificationAlwaysEnabled: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return true
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION, false)
        }

    var isLockScreenEnabled by BooleanSetting(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS)

    @JvmStatic
    var theme by StringSetting(
        Constants.PREFERENCES_KEY_THEME,
        Constants.PREFERENCES_KEY_THEME_DARK
    )

    @JvmStatic
    val maxBitRate: Int
        get() {
            val network = Util.networkInfo()

            if (!network.connected) return 0

            if (network.unmetered) {
                return maxWifiBitRate
            } else {
                return maxMobileBitRate
            }
        }

    private var maxWifiBitRate by StringIntSetting(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI)

    private var maxMobileBitRate by StringIntSetting(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE)

    @JvmStatic
    val preloadCount: Int
        get() {
            val preferences = preferences
            val preloadCount =
                preferences.getString(Constants.PREFERENCES_KEY_PRELOAD_COUNT, "-1")!!
                    .toInt()
            return if (preloadCount == -1) Int.MAX_VALUE else preloadCount
        }

    @JvmStatic
    val cacheSizeMB: Int
        get() {
            val preferences = preferences
            val cacheSize = preferences.getString(
                Constants.PREFERENCES_KEY_CACHE_SIZE,
                "-1"
            )!!.toInt()
            return if (cacheSize == -1) Int.MAX_VALUE else cacheSize
        }

    @JvmStatic
    var cacheLocation by StringSetting(
        Constants.PREFERENCES_KEY_CACHE_LOCATION,
        FileUtil.defaultMusicDirectory.path
    )

    @JvmStatic
    var isWifiRequiredForDownload by BooleanSetting(
        Constants.PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD,
        false
    )

    @JvmStatic
    var shareOnServer by BooleanSetting(Constants.PREFERENCES_KEY_SHARE_ON_SERVER, true)

    @JvmStatic
    var shouldDisplayBitrateWithArtist by BooleanSetting(
        Constants.PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST,
        true
    )

    @JvmStatic
    var shouldUseFolderForArtistName
    by BooleanSetting(Constants.PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST, false)

    @JvmStatic
    var shouldShowTrackNumber
    by BooleanSetting(Constants.PREFERENCES_KEY_SHOW_TRACK_NUMBER, false)

    @JvmStatic
    var defaultAlbums
    by StringIntSetting(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS, "5")

    @JvmStatic
    var maxAlbums
    by StringIntSetting(Constants.PREFERENCES_KEY_MAX_ALBUMS, "20")

    @JvmStatic
    var defaultSongs
    by StringIntSetting(Constants.PREFERENCES_KEY_DEFAULT_SONGS, "10")

    @JvmStatic
    var maxSongs
    by StringIntSetting(Constants.PREFERENCES_KEY_MAX_SONGS, "25")

    @JvmStatic
    var maxArtists
    by StringIntSetting(Constants.PREFERENCES_KEY_MAX_ARTISTS, "10")

    @JvmStatic
    var defaultArtists
    by StringIntSetting(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS, "3")

    @JvmStatic
    var bufferLength
    by StringIntSetting(Constants.PREFERENCES_KEY_BUFFER_LENGTH, "5")

    @JvmStatic
    var incrementTime
    by StringIntSetting(Constants.PREFERENCES_KEY_INCREMENT_TIME, "5")

    @JvmStatic
    var mediaButtonsEnabled
    by BooleanSetting(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true)

    @JvmStatic
    var showNowPlaying
    by BooleanSetting(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING, true)

    @JvmStatic
    var gaplessPlayback
    by BooleanSetting(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK, false)

    @JvmStatic
    var shouldTransitionOnPlayback by BooleanSetting(
        Constants.PREFERENCES_KEY_DOWNLOAD_TRANSITION,
        true
    )

    @JvmStatic
    var shouldUseId3Tags
    by BooleanSetting(Constants.PREFERENCES_KEY_ID3_TAGS, false)

    @JvmStatic
    var tempLoss by StringIntSetting(Constants.PREFERENCES_KEY_TEMP_LOSS, "1")

    var activeServer by IntSetting(Constants.PREFERENCES_KEY_SERVER_INSTANCE, -1)

    var serverScaling by BooleanSetting(Constants.PREFERENCES_KEY_SERVER_SCALING, false)

    var firstRunExecuted by BooleanSetting(Constants.PREFERENCES_KEY_FIRST_RUN_EXECUTED, false)

    val shouldShowArtistPicture: Boolean
        get() {
            val preferences = preferences
            val isOffline = ActiveServerProvider.isOffline()
            val isId3Enabled = preferences.getBoolean(Constants.PREFERENCES_KEY_ID3_TAGS, false)
            val shouldShowArtistPicture =
                preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_ARTIST_PICTURE, false)
            return !isOffline && isId3Enabled && shouldShowArtistPicture
        }

    @JvmStatic
    var chatRefreshInterval by StringIntSetting(
        Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL,
        "5000"
    )

    var directoryCacheTime by StringIntSetting(
        Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME,
        "300"
    )

    var shouldClearPlaylist
    by BooleanSetting(Constants.PREFERENCES_KEY_CLEAR_PLAYLIST, false)

    var shouldSortByDisc
    by BooleanSetting(Constants.PREFERENCES_KEY_DISC_SORT, false)

    var shouldClearBookmark
    by BooleanSetting(Constants.PREFERENCES_KEY_CLEAR_BOOKMARK, false)

    var singleButtonPlayPause
    by BooleanSetting(
        Constants.PREFERENCES_KEY_SINGLE_BUTTON_PLAY_PAUSE,
        false
    )

    // Inverted for readability
    var shouldSendBluetoothNotifications by BooleanSetting(
        Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS,
        true
    )

    var shouldSendBluetoothAlbumArt
    by BooleanSetting(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART, true)

    var shouldDisableNowPlayingListSending
    by BooleanSetting(Constants.PREFERENCES_KEY_DISABLE_SEND_NOW_PLAYING_LIST, false)

    @JvmStatic
    var viewRefreshInterval
    by StringIntSetting(Constants.PREFERENCES_KEY_VIEW_REFRESH, "1000")

    var shouldAskForShareDetails
    by BooleanSetting(Constants.PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS, true)

    var defaultShareDescription
    by StringSetting(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION, "")

    @JvmStatic
    val shareGreeting: String?
        get() {
            val preferences = preferences
            val context = Util.appContext()
            val defaultVal = String.format(
                context.resources.getString(R.string.share_default_greeting),
                context.resources.getString(R.string.common_appname)
            )
            return preferences.getString(
                Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING,
                defaultVal
            )
        }

    var defaultShareExpiration by StringSetting(
        Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION,
        "0"
    )

    val defaultShareExpirationInMillis: Long
        get() {
            val preferences = preferences
            val preference =
                preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION, "0")!!
            val split = PATTERN.split(preference)
            if (split.size == 2) {
                val timeSpanAmount = split[0].toInt()
                val timeSpanType = split[1]
                val timeSpan =
                    TimeSpanPicker.calculateTimeSpan(appContext, timeSpanType, timeSpanAmount)
                return timeSpan.totalMilliseconds
            }
            return 0
        }

    var shouldShowAllSongsByArtist by BooleanSetting(
        Constants.PREFERENCES_KEY_SHOW_ALL_SONGS_BY_ARTIST,
        false
    )

    @JvmStatic
    var resumeOnBluetoothDevice by IntSetting(
        Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE,
        Constants.PREFERENCE_VALUE_DISABLED
    )

    @JvmStatic
    var pauseOnBluetoothDevice by IntSetting(
        Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE,
        Constants.PREFERENCE_VALUE_A2DP
    )

    var debugLogToFile by BooleanSetting(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE, false)

    @JvmStatic
    val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Util.appContext())

    private val appContext: Context
        get() {
            return UApp.applicationContext()
        }
}
