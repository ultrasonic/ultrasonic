/*
 * Settings.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
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

    val isLockScreenEnabled: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS,
                false
            )
        }

    @JvmStatic
    val theme: String?
        get() {
            val preferences = preferences
            return preferences.getString(
                Constants.PREFERENCES_KEY_THEME,
                Constants.PREFERENCES_KEY_THEME_DARK
            )
        }

    @JvmStatic
    val maxBitRate: Int
        get() {
            val manager = Util.getConnectivityManager()
            val networkInfo = manager.activeNetworkInfo ?: return 0
            val wifi = networkInfo.type == ConnectivityManager.TYPE_WIFI
            val preferences = preferences
            return preferences.getString(
                if (wifi) Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI
                else Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE,
                "0"
            )!!.toInt()
        }

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

    val isWifiRequiredForDownload: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD,
                false
            )
        }

    val shouldDisplayBitrateWithArtist: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST,
                true
            )
        }

    @JvmStatic
    val shouldUseFolderForArtistName: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST,
                false
            )
        }

    val shouldShowTrackNumber: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_TRACK_NUMBER, false)
        }

    @JvmStatic
    val defaultAlbums: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS, "5")!!
                .toInt()
        }

    @JvmStatic
    val maxAlbums: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_MAX_ALBUMS, "20")!!
                .toInt()
        }

    @JvmStatic
    val defaultSongs: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SONGS, "10")!!
                .toInt()
        }

    @JvmStatic
    val maxSongs: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_MAX_SONGS, "25")!!
                .toInt()
        }

    @JvmStatic
    val maxArtists: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_MAX_ARTISTS, "10")!!
                .toInt()
        }

    @JvmStatic
    val defaultArtists: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS, "3")!!
                .toInt()
        }

    @JvmStatic
    val bufferLength: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_BUFFER_LENGTH, "5")!!
                .toInt()
        }

    @JvmStatic
    val incrementTime: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_INCREMENT_TIME, "5")!!
                .toInt()
        }

    @JvmStatic
    val mediaButtonsEnabled: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true)
        }

    @JvmStatic
    val showNowPlaying: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING, true)
        }

    @JvmStatic
    val gaplessPlayback: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK, false)
        }

    @JvmStatic
    val shouldTransitionOnPlayback: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_DOWNLOAD_TRANSITION, true)
        }

    @JvmStatic
    val shouldUseId3Tags: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_ID3_TAGS, false)
        }

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
    val chatRefreshInterval: Int
        get() {
            val preferences = preferences
            return preferences.getString(
                Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL,
                "5000"
            )!!.toInt()
        }

    val directoryCacheTime: Int
        get() {
            val preferences = preferences
            return preferences.getString(
                Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME,
                "300"
            )!!.toInt()
        }

    val shouldClearPlaylist: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_CLEAR_PLAYLIST, false)
        }

    val shouldSortByDisc: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_DISC_SORT, false)
        }

    val shouldClearBookmark: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_CLEAR_BOOKMARK, false)
        }

    val singleButtonPlayPause: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SINGLE_BUTTON_PLAY_PAUSE, false)
        }

    // Inverted for readability
    val shouldSendBluetoothNotifications: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS,
                true
            )
        }

    val shouldSendBluetoothAlbumArt: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART, true)
        }

    val shouldDisableNowPlayingListSending: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_DISABLE_SEND_NOW_PLAYING_LIST, false
            )
        }

    @JvmStatic
    val viewRefreshInterval: Int
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_VIEW_REFRESH, "1000")!!
                .toInt()
        }

    var shouldAskForShareDetails: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS, true)
        }
        set(shouldAskForShareDetails) {
            val preferences = preferences
            val editor = preferences.edit()
            editor.putBoolean(
                Constants.PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS,
                shouldAskForShareDetails
            )
            editor.apply()
        }

    var defaultShareDescription: String?
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION, "")
        }
        set(defaultShareDescription) {
            val preferences = preferences
            val editor = preferences.edit()
            editor.putString(
                Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION,
                defaultShareDescription
            )
            editor.apply()
        }

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

    var shareOnServer: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SHARE_ON_SERVER, true)!!
        }
        set(shareOnServer) {
            val preferences = preferences
            val editor = preferences.edit()
            editor.putBoolean(
                Constants.PREFERENCES_KEY_SHARE_ON_SERVER,
                shareOnServer
            )
            editor.apply()
        }

    var defaultShareExpiration: String
        get() {
            val preferences = preferences
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION, "0")!!
        }
        set(defaultShareExpiration) {
            val preferences = preferences
            val editor = preferences.edit()
            editor.putString(
                Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION,
                defaultShareExpiration
            )
            editor.apply()
        }

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

    val shouldShowAllSongsByArtist: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_ALL_SONGS_BY_ARTIST, false)
        }

    @JvmStatic
    val resumeOnBluetoothDevice: Int
        get() {
            val preferences = preferences
            return preferences.getInt(
                Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE,
                Constants.PREFERENCE_VALUE_DISABLED
            )
        }

    @JvmStatic
    val pauseOnBluetoothDevice: Int
        get() {
            val preferences = preferences
            return preferences.getInt(
                Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE,
                Constants.PREFERENCE_VALUE_A2DP
            )
        }

    val debugLogToFile: Boolean
        get() {
            val preferences = preferences
            return preferences.getBoolean(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE, false)
        }

    @JvmStatic
    val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Util.appContext())

    private val appContext: Context
        get() {
            return UApp.applicationContext()
        }
}
