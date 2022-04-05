/*
 * Settings.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.regex.Pattern
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider

/**
 * Contains convenience functions for reading and writing preferences
 */
object Settings {
    private val PATTERN = Pattern.compile(":")

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
    var customCacheLocation by BooleanSetting(
        Constants.PREFERENCES_KEY_CUSTOM_CACHE_LOCATION,
        false
    )

    @JvmStatic
    var cacheLocationUri by StringSetting(
        Constants.PREFERENCES_KEY_CACHE_LOCATION, ""
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
    var incrementTime
        by StringIntSetting(Constants.PREFERENCES_KEY_INCREMENT_TIME, "5")

    @JvmStatic
    var mediaButtonsEnabled
        by BooleanSetting(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true)

    @JvmStatic
    var showNowPlaying
        by BooleanSetting(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING, true)

    @JvmStatic
    var shouldTransitionOnPlayback by BooleanSetting(
        Constants.PREFERENCES_KEY_DOWNLOAD_TRANSITION,
        true
    )

    @JvmStatic
    var showNowPlayingDetails
        by BooleanSetting(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING_DETAILS, false)

    @JvmStatic
    var shouldUseId3Tags
        by BooleanSetting(Constants.PREFERENCES_KEY_ID3_TAGS, false)

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

    var shouldSortByDisc
        by BooleanSetting(Constants.PREFERENCES_KEY_DISC_SORT, false)

    var shouldClearBookmark
        by BooleanSetting(Constants.PREFERENCES_KEY_CLEAR_BOOKMARK, false)

    // Inverted for readability
    var shouldSendBluetoothNotifications by BooleanSetting(
        Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS,
        true
    )

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

    @JvmStatic
    var debugLogToFile by BooleanSetting(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE, false)

    @JvmStatic
    val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Util.appContext())

    @JvmStatic
    val overrideLanguage by StringSetting(Constants.PREFERENCES_KEY_OVERRIDE_LANGUAGE, "")

    var useFiveStarRating by BooleanSetting(Constants.PREFERENCES_KEY_USE_FIVE_STAR_RATING, false)

    var useHwOffload by BooleanSetting(Constants.PREFERENCES_KEY_HARDWARE_OFFLOAD, false)

    // TODO: Remove in December 2022
    fun migrateFeatureStorage() {
        val sp = appContext.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
        useFiveStarRating = sp.getBoolean("FIVE_STAR_RATING", false)
    }

    fun hasKey(key: String): Boolean {
        return preferences.contains(key)
    }

    private val appContext: Context
        get() {
            return UApp.applicationContext()
        }
}
