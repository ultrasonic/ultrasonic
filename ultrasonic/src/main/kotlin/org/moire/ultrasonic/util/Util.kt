/*
 * Util.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.support.v4.media.MediaDescriptionCompat
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.AnyRes
import androidx.media.utils.MediaConstants
import androidx.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.domain.RepeatMode
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.service.DownloadFile
import timber.log.Timber

private const val LINE_LENGTH = 60
private const val DEGRADE_PRECISION_AFTER = 10
private const val MINUTES_IN_HOUR = 60
private const val KBYTE = 1024

/**
 * Contains various utility functions
 */
@Suppress("TooManyFunctions", "LargeClass")
object Util {

    private val GIGA_BYTE_FORMAT = DecimalFormat("0.00 GB")
    private val MEGA_BYTE_FORMAT = DecimalFormat("0.00 MB")
    private val KILO_BYTE_FORMAT = DecimalFormat("0 KB")
    private val PATTERN = Pattern.compile(":")
    private var GIGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var MEGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var KILO_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    const val EVENT_META_CHANGED = "org.moire.ultrasonic.EVENT_META_CHANGED"
    const val EVENT_PLAYSTATE_CHANGED = "org.moire.ultrasonic.EVENT_PLAYSTATE_CHANGED"
    const val CM_AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
    const val CM_AVRCP_METADATA_CHANGED = "com.android.music.metachanged"

    // Used by hexEncode()
    private val HEX_DIGITS =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private var toast: Toast? = null
    private var currentSong: MusicDirectory.Entry? = null

    // Retrieves an instance of the application Context
    fun appContext(): Context {
        return applicationContext()
    }

    fun isScreenLitOnDownload(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(
            Constants.PREFERENCES_KEY_SCREEN_LIT_ON_DOWNLOAD,
            false
        )
    }

    var repeatMode: RepeatMode
        get() {
            val preferences = getPreferences()
            return RepeatMode.valueOf(
                preferences.getString(
                    Constants.PREFERENCES_KEY_REPEAT_MODE,
                    RepeatMode.OFF.name
                )!!
            )
        }
        set(repeatMode) {
            val preferences = getPreferences()
            val editor = preferences.edit()
            editor.putString(Constants.PREFERENCES_KEY_REPEAT_MODE, repeatMode.name)
            editor.apply()
        }

    // After API26 foreground services must be used for music playback,
    // and they must have a notification
    fun isNotificationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return true
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOTIFICATION, false)
    }

    // After API26 foreground services must be used for music playback,
    // and they must have a notification
    fun isNotificationAlwaysEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return true
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION, false)
    }

    fun isLockScreenEnabled(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(
            Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS,
            false
        )
    }

    @JvmStatic
    fun getTheme(): String? {
        val preferences = getPreferences()
        return preferences.getString(
            Constants.PREFERENCES_KEY_THEME,
            Constants.PREFERENCES_KEY_THEME_DARK
        )
    }

    @JvmStatic
    fun applyTheme(context: Context?) {
        val theme = getTheme()
        if (Constants.PREFERENCES_KEY_THEME_DARK.equals(
            theme,
            ignoreCase = true
        ) || "fullscreen".equals(theme, ignoreCase = true)
        ) {
            context!!.setTheme(R.style.UltrasonicTheme)
        } else if (Constants.PREFERENCES_KEY_THEME_BLACK.equals(theme, ignoreCase = true)) {
            context!!.setTheme(R.style.UltrasonicTheme_Black)
        } else if (Constants.PREFERENCES_KEY_THEME_LIGHT.equals(
            theme,
            ignoreCase = true
        ) || "fullscreenlight".equals(theme, ignoreCase = true)
        ) {
            context!!.setTheme(R.style.UltrasonicTheme_Light)
        }
    }

    private fun getConnectivityManager(): ConnectivityManager {
        val context = appContext()
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @JvmStatic
    fun getMaxBitRate(): Int {
        val manager = getConnectivityManager()
        val networkInfo = manager.activeNetworkInfo ?: return 0
        val wifi = networkInfo.type == ConnectivityManager.TYPE_WIFI
        val preferences = getPreferences()
        return preferences.getString(
            if (wifi) Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI
            else Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE,
            "0"
        )!!.toInt()
    }

    @JvmStatic
    fun getPreloadCount(): Int {
        val preferences = getPreferences()
        val preloadCount =
            preferences.getString(Constants.PREFERENCES_KEY_PRELOAD_COUNT, "-1")!!
                .toInt()
        return if (preloadCount == -1) Int.MAX_VALUE else preloadCount
    }

    @JvmStatic
    fun getCacheSizeMB(): Int {
        val preferences = getPreferences()
        val cacheSize = preferences.getString(
            Constants.PREFERENCES_KEY_CACHE_SIZE,
            "-1"
        )!!.toInt()
        return if (cacheSize == -1) Int.MAX_VALUE else cacheSize
    }

    @JvmStatic
    fun getPreferences(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext())

    /**
     * Get the contents of an `InputStream` as a `byte[]`.
     *
     *
     * This method buffers the input internally, so there is no need to use a
     * `BufferedInputStream`.
     *
     * @param input the `InputStream` to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws java.io.IOException  if an I/O error occurs
     */
    @Throws(IOException::class)
    fun toByteArray(input: InputStream?): ByteArray {
        val output = ByteArrayOutputStream()
        copy(input!!, output)
        return output.toByteArray()
    }

    @Throws(IOException::class)
    @Suppress("MagicNumber")
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(KBYTE * 4)
        var count: Long = 0
        var n: Int
        while (-1 != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
        }
        return count
    }

    @Throws(IOException::class)
    fun atomicCopy(from: File, to: File) {
        val tmp = File(String.format(Locale.ROOT, "%s.tmp", to.path))
        val input = FileInputStream(from)
        val out = FileOutputStream(tmp)
        try {
            input.channel.transferTo(0, from.length(), out.channel)
            out.close()
            if (!tmp.renameTo(to)) {
                throw IOException(
                    String.format(Locale.ROOT, "Failed to rename %s to %s", tmp, to)
                )
            }
            Timber.i("Copied %s to %s", from, to)
        } catch (x: IOException) {
            close(out)
            delete(to)
            throw x
        } finally {
            close(input)
            close(out)
            delete(tmp)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun renameFile(from: File, to: File) {
        if (from.renameTo(to)) {
            Timber.i("Renamed %s to %s", from, to)
        } else {
            atomicCopy(from, to)
        }
    }

    @JvmStatic
    fun close(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Throwable) {
            // Ignored
        }
    }

    @JvmStatic
    fun delete(file: File?): Boolean {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                Timber.w("Failed to delete file %s", file)
                return false
            }
            Timber.i("Deleted file %s", file)
        }
        return true
    }

    @JvmStatic
    @JvmOverloads
    fun toast(context: Context?, messageId: Int, shortDuration: Boolean = true) {
        toast(context, context!!.getString(messageId), shortDuration)
    }

    @JvmStatic
    fun toast(context: Context?, message: CharSequence?) {
        toast(context, message, true)
    }

    @JvmStatic
    @SuppressLint("ShowToast") // Invalid warning
    fun toast(context: Context?, message: CharSequence?, shortDuration: Boolean) {
        if (toast == null) {
            toast = Toast.makeText(
                context,
                message,
                if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            )
            toast!!.setGravity(Gravity.CENTER, 0, 0)
        } else {
            toast!!.setText(message)
            toast!!.duration =
                if (shortDuration) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        }
        toast!!.show()
    }

    /**
     * Formats an Int to a percentage string
     * For instance:
     *
     *  * `format(99)` returns *"99 %"*.
     *
     *
     * @param percent The percent as a range from 0 - 100
     * @return The formatted string.
     */
    @Synchronized
    fun formatPercentage(percent: Int): String {
        return min(max(percent, 0), 100).toString() + " %"
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * To get a localized string, please use formatLocalizedBytes instead.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @JvmStatic
    @Synchronized
    fun formatBytes(byteCount: Long): String {

        // More than 1 GB?
        if (byteCount >= KBYTE * KBYTE * KBYTE) {
            return GIGA_BYTE_FORMAT.format(byteCount.toDouble() / (KBYTE * KBYTE * KBYTE))
        }

        // More than 1 MB?
        if (byteCount >= KBYTE * KBYTE) {
            return MEGA_BYTE_FORMAT.format(byteCount.toDouble() / (KBYTE * KBYTE))
        }

        // More than 1 KB?
        return if (byteCount >= KBYTE) {
            KILO_BYTE_FORMAT.format(byteCount.toDouble() / KBYTE)
        } else "$byteCount B"
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     *
     *  * `format(918)` returns *"918 B"*.
     *  * `format(98765)` returns *"96 KB"*.
     *  * `format(1238476)` returns *"1.2 MB"*.
     *
     * This method assumes that 1 KB is 1024 bytes.
     * This version of the method returns a localized string.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    @Synchronized
    @Suppress("ReturnCount")
    fun formatLocalizedBytes(byteCount: Long, context: Context): String {

        // More than 1 GB?
        if (byteCount >= KBYTE * KBYTE * KBYTE) {
            if (GIGA_BYTE_LOCALIZED_FORMAT == null) {
                GIGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_gigabyte))
            }
            return GIGA_BYTE_LOCALIZED_FORMAT!!
                .format(byteCount.toDouble() / (KBYTE * KBYTE * KBYTE))
        }

        // More than 1 MB?
        if (byteCount >= KBYTE * KBYTE) {
            if (MEGA_BYTE_LOCALIZED_FORMAT == null) {
                MEGA_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_megabyte))
            }
            return MEGA_BYTE_LOCALIZED_FORMAT!!
                .format(byteCount.toDouble() / (KBYTE * KBYTE))
        }

        // More than 1 KB?
        if (byteCount >= KBYTE) {
            if (KILO_BYTE_LOCALIZED_FORMAT == null) {
                KILO_BYTE_LOCALIZED_FORMAT =
                    DecimalFormat(context.resources.getString(R.string.util_bytes_format_kilobyte))
            }
            return KILO_BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble() / KBYTE)
        }
        if (BYTE_LOCALIZED_FORMAT == null) {
            BYTE_LOCALIZED_FORMAT =
                DecimalFormat(context.resources.getString(R.string.util_bytes_format_byte))
        }
        return BYTE_LOCALIZED_FORMAT!!.format(byteCount.toDouble())
    }

    fun equals(object1: Any?, object2: Any?): Boolean {
        return object1 === object2 || !(object1 == null || object2 == null) && object1 == object2
    }

    /**
     * Encodes the given string by using the hexadecimal representation of its UTF-8 bytes.
     *
     * @param s The string to encode.
     * @return The encoded string.
     */
    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
    fun utf8HexEncode(s: String?): String? {
        if (s == null) {
            return null
        }
        val utf8: ByteArray = try {
            s.toByteArray(charset(Constants.UTF_8))
        } catch (x: UnsupportedEncodingException) {
            throw RuntimeException(x)
        }
        return hexEncode(utf8)
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data Bytes to convert to hexadecimal characters.
     * @return A string containing hexadecimal characters.
     */
    @Suppress("MagicNumber")
    fun hexEncode(data: ByteArray): String {
        val length = data.size
        val out = CharArray(length shl 1)
        var j = 0

        // two characters form the hex value.
        for (aData in data) {
            out[j++] = HEX_DIGITS[0xF0 and aData.toInt() ushr 4]
            out[j++] = HEX_DIGITS[0x0F and aData.toInt()]
        }
        return String(out)
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hex string.
     *
     * @param s Data to digest.
     * @return MD5 digest as a hex string.
     */
    @JvmStatic
    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
    fun md5Hex(s: String?): String? {
        return if (s == null) {
            null
        } else try {
            val md5 = MessageDigest.getInstance("MD5")
            hexEncode(md5.digest(s.toByteArray(charset(Constants.UTF_8))))
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }

    @JvmStatic
    fun getGrandparent(path: String?): String? {
        // Find the top level folder, assume it is the album artist
        if (path != null) {
            val slashIndex = path.indexOf('/')
            if (slashIndex > 0) {
                return path.substring(0, slashIndex)
            }
        }
        return null
    }

    @JvmStatic
    fun isNetworkConnected(): Boolean {
        val manager = getConnectivityManager()
        val networkInfo = manager.activeNetworkInfo
        val connected = networkInfo != null && networkInfo.isConnected
        val wifiConnected = connected && networkInfo!!.type == ConnectivityManager.TYPE_WIFI
        val wifiRequired = isWifiRequiredForDownload()
        return connected && (!wifiRequired || wifiConnected)
    }

    @JvmStatic
    fun isExternalStoragePresent(): Boolean =
        Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    fun isWifiRequiredForDownload(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(
            Constants.PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD,
            false
        )
    }

    fun shouldDisplayBitrateWithArtist(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(
            Constants.PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST,
            true
        )
    }

    @JvmStatic
    fun shouldUseFolderForArtistName(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(
            Constants.PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST,
            false
        )
    }

    fun shouldShowTrackNumber(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_TRACK_NUMBER, false)
    }

    // The AlertDialog requires an Activity context, app context is not enough
    // See https://stackoverflow.com/questions/5436822/
    fun showDialog(context: Context?, icon: Int, titleId: Int, message: String?) {
        AlertDialog.Builder(context)
            .setIcon(icon)
            .setTitle(titleId)
            .setMessage(message)
            .setPositiveButton(R.string.common_ok) {
                dialog: DialogInterface,
                _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    @JvmStatic
    fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (x: InterruptedException) {
            Timber.w(x, "Interrupted from sleep.")
        }
    }

    @JvmStatic
    fun getDrawableFromAttribute(context: Context?, attr: Int): Drawable {
        val attrs = intArrayOf(attr)
        val ta = context!!.obtainStyledAttributes(attrs)
        val drawableFromTheme: Drawable? = ta.getDrawable(0)
        ta.recycle()
        return drawableFromTheme!!
    }

    fun createDrawableFromBitmap(context: Context, bitmap: Bitmap?): Drawable {
        return BitmapDrawable(context.resources, bitmap)
    }

    fun createBitmapFromDrawable(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun createWifiLock(tag: String?): WifiLock {
        val wm =
            appContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
    }

    fun getScaledHeight(height: Double, width: Double, newWidth: Int): Int {
        // Try to keep correct aspect ratio of the original image, do not force a square
        val aspectRatio = height / width

        // Assume the size given refers to the width of the image, so calculate the new height using
        // the previously determined aspect ratio
        return Math.round(newWidth * aspectRatio).toInt()
    }

    fun getScaledHeight(bitmap: Bitmap, width: Int): Int {
        return getScaledHeight(bitmap.height.toDouble(), bitmap.width.toDouble(), width)
    }

    fun scaleBitmap(bitmap: Bitmap?, size: Int): Bitmap? {
        return if (bitmap == null) null else Bitmap.createScaledBitmap(
            bitmap,
            size,
            getScaledHeight(bitmap, size),
            true
        )
    }

    fun getSongsFromSearchResult(searchResult: SearchResult): MusicDirectory {
        val musicDirectory = MusicDirectory()
        for (entry in searchResult.songs) {
            musicDirectory.addChild(entry)
        }
        return musicDirectory
    }

    @JvmStatic
    fun getSongsFromBookmarks(bookmarks: Iterable<Bookmark?>): MusicDirectory {
        val musicDirectory = MusicDirectory()
        var song: MusicDirectory.Entry
        for (bookmark in bookmarks) {
            if (bookmark == null) continue
            song = bookmark.entry
            song.bookmarkPosition = bookmark.position
            musicDirectory.addChild(song)
        }
        return musicDirectory
    }

    /**
     *
     * Broadcasts the given song info as the new song being played.
     */
    fun broadcastNewTrackInfo(context: Context, song: MusicDirectory.Entry?) {
        val intent = Intent(EVENT_META_CHANGED)
        if (song != null) {
            intent.putExtra("title", song.title)
            intent.putExtra("artist", song.artist)
            intent.putExtra("album", song.album)
            val albumArtFile = FileUtil.getAlbumArtFile(song)
            intent.putExtra("coverart", albumArtFile.absolutePath)
        } else {
            intent.putExtra("title", "")
            intent.putExtra("artist", "")
            intent.putExtra("album", "")
            intent.putExtra("coverart", "")
        }
        context.sendBroadcast(intent)
    }

    fun broadcastA2dpMetaDataChange(
        context: Context,
        playerPosition: Int,
        currentPlaying: DownloadFile?,
        listSize: Int,
        id: Int
    ) {
        if (!shouldSendBluetoothNotifications) {
            return
        }
        var song: MusicDirectory.Entry? = null
        val avrcpIntent = Intent(CM_AVRCP_METADATA_CHANGED)
        if (currentPlaying != null) song = currentPlaying.song

        if (song == null) {
            avrcpIntent.putExtra("track", "")
            avrcpIntent.putExtra("track_name", "")
            avrcpIntent.putExtra("artist", "")
            avrcpIntent.putExtra("artist_name", "")
            avrcpIntent.putExtra("album", "")
            avrcpIntent.putExtra("album_name", "")
            avrcpIntent.putExtra("album_artist", "")
            avrcpIntent.putExtra("album_artist_name", "")

            if (getShouldSendBluetoothAlbumArt()) {
                avrcpIntent.putExtra("coverart", null as Parcelable?)
                avrcpIntent.putExtra("cover", null as Parcelable?)
            }

            avrcpIntent.putExtra("ListSize", 0.toLong())
            avrcpIntent.putExtra("id", 0.toLong())
            avrcpIntent.putExtra("duration", 0.toLong())
            avrcpIntent.putExtra("position", 0.toLong())
        } else {
            if (song !== currentSong) {
                currentSong = song
            }
            val title = song.title
            val artist = song.artist
            val album = song.album
            val duration = song.duration

            avrcpIntent.putExtra("track", title)
            avrcpIntent.putExtra("track_name", title)
            avrcpIntent.putExtra("artist", artist)
            avrcpIntent.putExtra("artist_name", artist)
            avrcpIntent.putExtra("album", album)
            avrcpIntent.putExtra("album_name", album)
            avrcpIntent.putExtra("album_artist", artist)
            avrcpIntent.putExtra("album_artist_name", artist)

            if (getShouldSendBluetoothAlbumArt()) {
                val albumArtFile = FileUtil.getAlbumArtFile(song)
                avrcpIntent.putExtra("coverart", albumArtFile.absolutePath)
                avrcpIntent.putExtra("cover", albumArtFile.absolutePath)
            }

            avrcpIntent.putExtra("position", playerPosition.toLong())
            avrcpIntent.putExtra("id", id.toLong())
            avrcpIntent.putExtra("ListSize", listSize.toLong())

            if (duration != null) {
                avrcpIntent.putExtra("duration", duration.toLong())
            }
        }
        context.sendBroadcast(avrcpIntent)
    }

    @Suppress("LongParameterList")
    fun broadcastA2dpPlayStatusChange(
        context: Context,
        state: PlayerState?,
        currentSong: MusicDirectory.Entry?,
        listSize: Int,
        id: Int,
        playerPosition: Int
    ) {
        if (!shouldSendBluetoothNotifications) {
            return
        }
        if (currentSong != null) {
            val avrcpIntent = Intent(CM_AVRCP_PLAYSTATE_CHANGED)
            if (currentSong == null) {
                return
            }

            // FIXME This is probably a bug.
            if (currentSong !== currentSong) {
                Util.currentSong = currentSong
            }
            val title = currentSong.title
            val artist = currentSong.artist
            val album = currentSong.album
            val duration = currentSong.duration

            avrcpIntent.putExtra("track", title)
            avrcpIntent.putExtra("track_name", title)
            avrcpIntent.putExtra("artist", artist)
            avrcpIntent.putExtra("artist_name", artist)
            avrcpIntent.putExtra("album", album)
            avrcpIntent.putExtra("album_name", album)
            avrcpIntent.putExtra("album_artist", artist)
            avrcpIntent.putExtra("album_artist_name", artist)

            if (getShouldSendBluetoothAlbumArt()) {
                val albumArtFile = FileUtil.getAlbumArtFile(currentSong)
                avrcpIntent.putExtra("coverart", albumArtFile.absolutePath)
                avrcpIntent.putExtra("cover", albumArtFile.absolutePath)
            }

            avrcpIntent.putExtra("position", playerPosition.toLong())
            avrcpIntent.putExtra("id", id.toLong())
            avrcpIntent.putExtra("ListSize", listSize.toLong())

            if (duration != null) {
                avrcpIntent.putExtra("duration", duration.toLong())
            }

            when (state) {
                PlayerState.STARTED -> avrcpIntent.putExtra("playing", true)
                PlayerState.STOPPED, PlayerState.PAUSED,
                PlayerState.COMPLETED -> avrcpIntent.putExtra(
                    "playing",
                    false
                )
                else -> return // No need to broadcast.
            }

            context.sendBroadcast(avrcpIntent)
        }
    }

    /**
     *
     * Broadcasts the given player state as the one being set.
     */
    fun broadcastPlaybackStatusChange(context: Context, state: PlayerState?) {
        val intent = Intent(EVENT_PLAYSTATE_CHANGED)
        when (state) {
            PlayerState.STARTED -> intent.putExtra("state", "play")
            PlayerState.STOPPED -> intent.putExtra("state", "stop")
            PlayerState.PAUSED -> intent.putExtra("state", "pause")
            PlayerState.COMPLETED -> intent.putExtra("state", "complete")
            else -> return // No need to broadcast.
        }
        context.sendBroadcast(intent)
    }

    @JvmStatic
    @Suppress("MagicNumber")
    fun getNotificationImageSize(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val imageSizeLarge =
            min(metrics.widthPixels, metrics.heightPixels).toFloat().roundToInt()
        return when {
            imageSizeLarge <= 480 -> {
                64
            }
            imageSizeLarge <= 768 -> 128
            else -> 256
        }
    }

    @Suppress("MagicNumber")
    fun getAlbumImageSize(context: Context?): Int {
        val metrics = context!!.resources.displayMetrics
        val imageSizeLarge =
            min(metrics.widthPixels, metrics.heightPixels).toFloat().roundToInt()
        return when {
            imageSizeLarge <= 480 -> {
                128
            }
            imageSizeLarge <= 768 -> 256
            else -> 512
        }
    }

    fun getMinDisplayMetric(): Int {
        val metrics = appContext().resources.displayMetrics
        return Math.min(metrics.widthPixels, metrics.heightPixels)
    }

    fun getMaxDisplayMetric(): Int {
        val metrics = appContext().resources.displayMetrics
        return Math.max(metrics.widthPixels, metrics.heightPixels)
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = Math.min(heightRatio, widthRatio)
        }
        return inSampleSize
    }

    @JvmStatic
    fun getDefaultAlbums(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS, "5")!!
            .toInt()
    }

    @JvmStatic
    fun getMaxAlbums(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_MAX_ALBUMS, "20")!!
            .toInt()
    }

    @JvmStatic
    fun getDefaultSongs(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SONGS, "10")!!
            .toInt()
    }

    @JvmStatic
    fun getMaxSongs(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_MAX_SONGS, "25")!!
            .toInt()
    }

    @JvmStatic
    fun getMaxArtists(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_MAX_ARTISTS, "10")!!
            .toInt()
    }

    @JvmStatic
    fun getDefaultArtists(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS, "3")!!
            .toInt()
    }

    @JvmStatic
    fun getBufferLength(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_BUFFER_LENGTH, "5")!!
            .toInt()
    }

    @JvmStatic
    fun getIncrementTime(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_INCREMENT_TIME, "5")!!
            .toInt()
    }

    @JvmStatic
    fun getMediaButtonsEnabled(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true)
    }

    @JvmStatic
    fun getShowNowPlayingPreference(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING, true)
    }

    @JvmStatic
    fun getGaplessPlaybackPreference(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK, false)
    }

    @JvmStatic
    fun getShouldTransitionOnPlaybackPreference(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_DOWNLOAD_TRANSITION, true)
    }

    @JvmStatic
    fun getShouldUseId3Tags(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_ID3_TAGS, false)
    }

    fun getShouldShowArtistPicture(): Boolean {
        val preferences = getPreferences()
        val isOffline = isOffline()
        val isId3Enabled = preferences.getBoolean(Constants.PREFERENCES_KEY_ID3_TAGS, false)
        val shouldShowArtistPicture =
            preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_ARTIST_PICTURE, false)
        return !isOffline && isId3Enabled && shouldShowArtistPicture
    }

    @JvmStatic
    fun getChatRefreshInterval(): Int {
        val preferences = getPreferences()
        return preferences.getString(
            Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL,
            "5000"
        )!!.toInt()
    }

    fun getDirectoryCacheTime(): Int {
        val preferences = getPreferences()
        return preferences.getString(
            Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME,
            "300"
        )!!.toInt()
    }

    @JvmStatic
    fun isNullOrWhiteSpace(string: String?): Boolean {
        return string == null || string.isEmpty() || string.trim { it <= ' ' }.isEmpty()
    }

    fun getShouldClearPlaylist(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_CLEAR_PLAYLIST, false)
    }

    fun getShouldSortByDisc(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_DISC_SORT, false)
    }

    fun getShouldClearBookmark(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_CLEAR_BOOKMARK, false)
    }

    fun getSingleButtonPlayPause(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SINGLE_BUTTON_PLAY_PAUSE, false)
    }

    @JvmOverloads
    fun formatTotalDuration(totalDuration: Long, inMilliseconds: Boolean = false): String {
        var millis = totalDuration
        if (!inMilliseconds) {
            millis = totalDuration * 1000
        }
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
            TimeUnit.MINUTES.toSeconds(hours * MINUTES_IN_HOUR + minutes)

        return when {
            hours >= DEGRADE_PRECISION_AFTER -> {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            }
            hours > 0 -> {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            }
            minutes >= DEGRADE_PRECISION_AFTER -> {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
            minutes > 0 -> String.format(
                Locale.getDefault(),
                "%d:%02d",
                minutes,
                seconds
            )
            else -> String.format(Locale.getDefault(), "0:%02d", seconds)
        }
    }

    @JvmStatic
    fun getVersionName(context: Context): String? {
        var versionName: String? = null
        val pm = context.packageManager
        if (pm != null) {
            val packageName = context.packageName
            try {
                versionName = pm.getPackageInfo(packageName, 0).versionName
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return versionName
    }

    fun getVersionCode(context: Context): Int {
        var versionCode = 0
        val pm = context.packageManager
        if (pm != null) {
            val packageName = context.packageName
            try {
                versionCode = pm.getPackageInfo(packageName, 0).versionCode
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return versionCode
    }

    // Inverted for readability
    val shouldSendBluetoothNotifications: Boolean
        get() {
            val preferences = getPreferences()
            return preferences.getBoolean(
                Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS,
                true
            )
        }

    fun getShouldSendBluetoothAlbumArt(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART, true)
    }

    @JvmStatic
    fun getViewRefreshInterval(): Int {
        val preferences = getPreferences()
        return preferences.getString(Constants.PREFERENCES_KEY_VIEW_REFRESH, "1000")!!
            .toInt()
    }

    var shouldAskForShareDetails: Boolean
        get() {
            val preferences = getPreferences()
            return preferences.getBoolean(Constants.PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS, true)
        }
        set(shouldAskForShareDetails) {
            val preferences = getPreferences()
            val editor = preferences.edit()
            editor.putBoolean(
                Constants.PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS,
                shouldAskForShareDetails
            )
            editor.apply()
        }

    var defaultShareDescription: String?
        get() {
            val preferences = getPreferences()
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION, "")
        }
        set(defaultShareDescription) {
            val preferences = getPreferences()
            val editor = preferences.edit()
            editor.putString(
                Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION,
                defaultShareDescription
            )
            editor.apply()
        }

    @JvmStatic
    fun getShareGreeting(): String? {
        val preferences = getPreferences()
        val context = appContext()
        val defaultVal = String.format(
            context.resources.getString(R.string.share_default_greeting),
            context.resources.getString(R.string.common_appname)
        )
        return preferences.getString(
            Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING,
            defaultVal
        )
    }

    var defaultShareExpiration: String
        get() {
            val preferences = getPreferences()
            return preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION, "0")!!
        }
        set(defaultShareExpiration) {
            val preferences = getPreferences()
            val editor = preferences.edit()
            editor.putString(
                Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION,
                defaultShareExpiration
            )
            editor.apply()
        }

    fun getDefaultShareExpirationInMillis(context: Context?): Long {
        val preferences = getPreferences()
        val preference =
            preferences.getString(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION, "0")!!
        val split = PATTERN.split(preference)
        if (split.size == 2) {
            val timeSpanAmount = split[0].toInt()
            val timeSpanType = split[1]
            val timeSpan = TimeSpanPicker.calculateTimeSpan(context, timeSpanType, timeSpanAmount)
            return timeSpan.totalMilliseconds
        }
        return 0
    }

    fun getShouldShowAllSongsByArtist(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_SHOW_ALL_SONGS_BY_ARTIST, false)
    }

    @JvmStatic
    fun scanMedia(file: File?) {
        val uri = Uri.fromFile(file)
        val scanFileIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
        appContext().sendBroadcast(scanFileIntent)
    }

    fun imageLoaderConcurrency(): Int {
        val preferences = getPreferences()
        return preferences.getString(
            Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY,
            "5"
        )!!.toInt()
    }

    fun getResourceFromAttribute(context: Context, resId: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resId, typedValue, true)
        return typedValue.resourceId
    }

    fun isFirstRun(): Boolean {
        val preferences = getPreferences()
        val firstExecuted =
            preferences.getBoolean(Constants.PREFERENCES_KEY_FIRST_RUN_EXECUTED, false)
        if (firstExecuted) return false
        val editor = preferences.edit()
        editor.putBoolean(Constants.PREFERENCES_KEY_FIRST_RUN_EXECUTED, true)
        editor.apply()
        return true
    }

    @JvmStatic
    fun getResumeOnBluetoothDevice(): Int {
        val preferences = getPreferences()
        return preferences.getInt(
            Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE,
            Constants.PREFERENCE_VALUE_DISABLED
        )
    }

    @JvmStatic
    fun getPauseOnBluetoothDevice(): Int {
        val preferences = getPreferences()
        return preferences.getInt(
            Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE,
            Constants.PREFERENCE_VALUE_A2DP
        )
    }

    fun getDebugLogToFile(): Boolean {
        val preferences = getPreferences()
        return preferences.getBoolean(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE, false)
    }

    fun hideKeyboard(activity: Activity?) {
        val inputManager =
            activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = activity.currentFocus
        if (currentFocusedView != null) {
            inputManager.hideSoftInputFromWindow(
                currentFocusedView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    fun getUriToDrawable(context: Context, @AnyRes drawableId: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.resources.getResourcePackageName(drawableId) +
                '/' + context.resources.getResourceTypeName(drawableId) +
                '/' + context.resources.getResourceEntryName(drawableId)
        )
    }

    @Suppress("ComplexMethod", "LongMethod")
    fun getMediaDescriptionForEntry(
        song: MusicDirectory.Entry,
        mediaId: String? = null,
        groupNameId: Int? = null
    ): MediaDescriptionCompat {

        val descriptionBuilder = MediaDescriptionCompat.Builder()
        val artist = StringBuilder(LINE_LENGTH)
        var bitRate: String? = null

        val duration = song.duration
        if (duration != null) {
            artist.append(
                String.format(Locale.ROOT, "%s  ", formatTotalDuration(duration.toLong()))
            )
        }

        if (song.bitRate != null && song.bitRate!! > 0)
            bitRate = String.format(
                appContext().getString(R.string.song_details_kbps), song.bitRate
            )

        val fileFormat: String?
        val suffix = song.suffix
        val transcodedSuffix = song.transcodedSuffix

        fileFormat = if (
            TextUtils.isEmpty(transcodedSuffix) || transcodedSuffix == suffix || song.isVideo
        ) suffix else String.format(Locale.ROOT, "%s > %s", suffix, transcodedSuffix)

        val artistName = song.artist

        if (artistName != null) {
            if (shouldDisplayBitrateWithArtist() && (
                !bitRate.isNullOrBlank() || !fileFormat.isNullOrBlank()
                )
            ) {
                artist.append(artistName).append(" (").append(
                    String.format(
                        appContext().getString(R.string.song_details_all),
                        if (bitRate == null) ""
                        else String.format(Locale.ROOT, "%s ", bitRate),
                        fileFormat
                    )
                ).append(')')
            } else {
                artist.append(artistName)
            }
        }

        val trackNumber = song.track ?: 0

        val title = StringBuilder(LINE_LENGTH)
        if (shouldShowTrackNumber() && trackNumber > 0)
            title.append(String.format(Locale.ROOT, "%02d - ", trackNumber))

        title.append(song.title)

        if (song.isVideo && shouldDisplayBitrateWithArtist()) {
            title.append(" (").append(
                String.format(
                    appContext().getString(R.string.song_details_all),
                    if (bitRate == null) ""
                    else String.format(Locale.ROOT, "%s ", bitRate),
                    fileFormat
                )
            ).append(')')
        }

        if (groupNameId != null)
            descriptionBuilder.setExtras(
                Bundle().apply {
                    putString(
                        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                        appContext().getString(groupNameId)
                    )
                }
            )

        descriptionBuilder.setTitle(title)
        descriptionBuilder.setSubtitle(artist)
        descriptionBuilder.setMediaId(mediaId)

        return descriptionBuilder.build()
    }

    fun getPendingIntentForMediaAction(
        context: Context,
        keycode: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(Constants.CMD_PROCESS_KEYCODE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.setPackage(context.packageName)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
