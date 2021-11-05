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
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
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
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
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
    private var GIGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var MEGA_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var KILO_BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private var BYTE_LOCALIZED_FORMAT: DecimalFormat? = null
    private const val EVENT_META_CHANGED = "org.moire.ultrasonic.EVENT_META_CHANGED"
    private const val EVENT_PLAYSTATE_CHANGED = "org.moire.ultrasonic.EVENT_PLAYSTATE_CHANGED"
    private const val CM_AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
    private const val CM_AVRCP_PLAYBACK_COMPLETE = "com.android.music.playbackcomplete"
    private const val CM_AVRCP_METADATA_CHANGED = "com.android.music.metachanged"

    // Used by hexEncode()
    private val HEX_DIGITS =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private var toast: Toast? = null

    // Retrieves an instance of the application Context
    fun appContext(): Context {
        return applicationContext()
    }

    @JvmStatic
    fun applyTheme(context: Context?) {
        when (Settings.theme.lowercase()) {
            Constants.PREFERENCES_KEY_THEME_DARK,
            "fullscreen" -> {
                context!!.setTheme(R.style.UltrasonicTheme)
            }
            Constants.PREFERENCES_KEY_THEME_BLACK -> {
                context!!.setTheme(R.style.UltrasonicTheme_Black)
            }
            Constants.PREFERENCES_KEY_THEME_LIGHT,
            "fullscreenlight" -> {
                context!!.setTheme(R.style.UltrasonicTheme_Light)
            }
        }
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

    @Suppress("SuspiciousEqualsCombination")
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

    /**
     * Check if a usable network for downloading media is available
     *
     * @return Boolean
     */
    @JvmStatic
    fun isNetworkConnected(): Boolean {
        val info = networkInfo()
        val isUnmetered = info.unmetered
        val wifiRequired = Settings.isWifiRequiredForDownload
        return info.connected && (!wifiRequired || isUnmetered)
    }

    /**
     * Query connectivity status
     *
     * @return NetworkInfo object
     */
    @Suppress("DEPRECATION")
    fun networkInfo(): NetworkInfo {
        val manager = getConnectivityManager()
        val info = NetworkInfo()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network: Network? = manager.activeNetwork
            val capabilities = manager.getNetworkCapabilities(network)

            if (capabilities != null) {
                info.unmetered = capabilities.hasCapability(NET_CAPABILITY_NOT_METERED)
                info.connected = capabilities.hasCapability(NET_CAPABILITY_INTERNET)
            }
        } else {
            val networkInfo = manager.activeNetworkInfo
            if (networkInfo != null) {
                info.unmetered = networkInfo.type == ConnectivityManager.TYPE_WIFI
                info.connected = networkInfo.isConnected
            }
        }
        return info
    }

    @JvmStatic
    fun isExternalStoragePresent(): Boolean =
        Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    // The AlertDialog requires an Activity context, app context is not enough
    // See https://stackoverflow.com/questions/5436822/
    fun createDialog(
        context: Context?,
        icon: Int = android.R.drawable.ic_dialog_info,
        title: String,
        message: String?
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setIcon(icon)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.common_ok) {
                dialog: DialogInterface,
                _: Int ->
                dialog.dismiss()
            }
    }

    fun showDialog(
        context: Context,
        icon: Int = android.R.drawable.ic_dialog_info,
        titleId: Int,
        message: String?
    ) {
        createDialog(context, icon, context.getString(titleId, ""), message).show()
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
        return (newWidth * aspectRatio).roundToInt()
    }

    private fun getScaledHeight(bitmap: Bitmap, width: Int): Int {
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
        if (!Settings.shouldSendBluetoothNotifications) return

        var song: MusicDirectory.Entry? = null
        val avrcpIntent = Intent(CM_AVRCP_METADATA_CHANGED)
        if (currentPlaying != null) song = currentPlaying.song

        fillIntent(avrcpIntent, song, playerPosition, id, listSize)

        context.sendBroadcast(avrcpIntent)
    }

    @Suppress("LongParameterList")
    fun broadcastA2dpPlayStatusChange(
        context: Context,
        state: PlayerState?,
        newSong: MusicDirectory.Entry?,
        listSize: Int,
        id: Int,
        playerPosition: Int
    ) {
        if (!Settings.shouldSendBluetoothNotifications) return

        if (newSong != null) {

            val avrcpIntent = Intent(
                if (state == PlayerState.COMPLETED) CM_AVRCP_PLAYBACK_COMPLETE
                else CM_AVRCP_PLAYSTATE_CHANGED
            )

            fillIntent(avrcpIntent, newSong, playerPosition, id, listSize)

            if (state != PlayerState.COMPLETED) {
                when (state) {
                    PlayerState.STARTED -> avrcpIntent.putExtra("playing", true)
                    PlayerState.STOPPED,
                    PlayerState.PAUSED -> avrcpIntent.putExtra("playing", false)
                    else -> return // No need to broadcast.
                }
            }

            context.sendBroadcast(avrcpIntent)
        }
    }

    private fun fillIntent(
        intent: Intent,
        song: MusicDirectory.Entry?,
        playerPosition: Int,
        id: Int,
        listSize: Int
    ) {
        if (song == null) {
            intent.putExtra("track", "")
            intent.putExtra("track_name", "")
            intent.putExtra("artist", "")
            intent.putExtra("artist_name", "")
            intent.putExtra("album", "")
            intent.putExtra("album_name", "")
            intent.putExtra("album_artist", "")
            intent.putExtra("album_artist_name", "")

            if (Settings.shouldSendBluetoothAlbumArt) {
                intent.putExtra("coverart", null as Parcelable?)
                intent.putExtra("cover", null as Parcelable?)
            }

            intent.putExtra("ListSize", 0.toLong())
            intent.putExtra("id", 0.toLong())
            intent.putExtra("duration", 0.toLong())
            intent.putExtra("position", 0.toLong())
        } else {
            val title = song.title
            val artist = song.artist
            val album = song.album
            val duration = song.duration

            intent.putExtra("track", title)
            intent.putExtra("track_name", title)
            intent.putExtra("artist", artist)
            intent.putExtra("artist_name", artist)
            intent.putExtra("album", album)
            intent.putExtra("album_name", album)
            intent.putExtra("album_artist", artist)
            intent.putExtra("album_artist_name", artist)

            if (Settings.shouldSendBluetoothAlbumArt) {
                val albumArtFile = FileUtil.getAlbumArtFile(song)
                intent.putExtra("coverart", albumArtFile.absolutePath)
                intent.putExtra("cover", albumArtFile.absolutePath)
            }

            intent.putExtra("position", playerPosition.toLong())
            intent.putExtra("id", id.toLong())
            intent.putExtra("ListSize", listSize.toLong())

            if (duration != null) {
                intent.putExtra("duration", duration.toLong())
            }
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
        return min(metrics.widthPixels, metrics.heightPixels)
    }

    fun getMaxDisplayMetric(): Int {
        val metrics = appContext().resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels)
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            val heightRatio = (height.toFloat() / reqHeight.toFloat()).roundToInt()
            val widthRatio = (width.toFloat() / reqWidth.toFloat()).roundToInt()

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = min(heightRatio, widthRatio)
        }
        return inSampleSize
    }

    @JvmStatic
    fun isNullOrWhiteSpace(string: String?): Boolean {
        return string == null || string.isEmpty() || string.trim { it <= ' ' }.isEmpty()
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

    @JvmStatic
    fun scanMedia(file: File?) {
        val uri = Uri.fromFile(file)
        val scanFileIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
        appContext().sendBroadcast(scanFileIntent)
    }

    fun getResourceFromAttribute(context: Context, resId: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resId, typedValue, true)
        return typedValue.resourceId
    }

    fun isFirstRun(): Boolean {
        if (Settings.firstRunExecuted) return false

        Settings.firstRunExecuted = true
        return true
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
            if (Settings.shouldDisplayBitrateWithArtist && (
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
        if (Settings.shouldShowTrackNumber && trackNumber > 0)
            title.append(String.format(Locale.ROOT, "%02d - ", trackNumber))

        title.append(song.title)

        if (song.isVideo && Settings.shouldDisplayBitrateWithArtist) {
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

    fun getConnectivityManager(): ConnectivityManager {
        val context = appContext()
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    data class NetworkInfo(
        var connected: Boolean = false,
        var unmetered: Boolean = false
    )
}
