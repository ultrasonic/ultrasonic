/*
 * MediaStoreService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.moire.ultrasonic.util.FileUtil
import timber.log.Timber

/**
 * By adding UltraSonics media files to the Android MediaStore
 * they become available in the stock music apps
 *
 * @author Sindre Mehus
 */

class MediaStoreService(private val context: Context) {

    // Find the audio collection on the primary external storage device.
    val collection: Uri by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    val albumArtCollection: Uri by lazy {
        // This path is not well documented
        // https://android.googlesource.com/platform/packages/providers/
        // MediaProvider/+/refs/tags/android-platform-11.0.0_r5/
        // src/com/android/providers/media/MediaProvider.java#7596

        Uri.parse(collection.toString().replaceAfterLast("/", "albumart"))
    }

    fun saveInMediaStore(downloadFile: DownloadFile) {
        val song = downloadFile.song
        val songFile = downloadFile.completeFile

        // Delete existing row in case the song has been downloaded before.
        deleteFromMediaStore(downloadFile)
        val contentResolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.TITLE, song.title)
        values.put(MediaStore.Audio.AudioColumns.ARTIST, song.artist)
        values.put(MediaStore.Audio.AudioColumns.ALBUM, song.album)
        values.put(MediaStore.Audio.AudioColumns.TRACK, song.track)
        values.put(MediaStore.Audio.AudioColumns.YEAR, song.year)
        values.put(MediaStore.MediaColumns.DATA, songFile.absolutePath)
        values.put(MediaStore.MediaColumns.MIME_TYPE, song.contentType)
        values.put(MediaStore.Audio.AudioColumns.IS_MUSIC, 1)

        val uri = contentResolver.insert(collection, values)

        if (uri != null) {
            // Look up album, and add cover art if found.
            val cursor = contentResolver.query(
                uri, arrayOf(MediaStore.Audio.AudioColumns.ALBUM_ID),
                null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val albumId = cursor.getInt(0)
                insertAlbumArt(albumId, downloadFile)
                cursor.close()
            }
        }
    }

    fun deleteFromMediaStore(downloadFile: DownloadFile) {
        val contentResolver = context.contentResolver
        val song = downloadFile.song
        val file = downloadFile.completeFile

        val selection = MediaStore.Audio.AudioColumns.TITLE_KEY + "=? AND " +
            MediaStore.MediaColumns.DATA + "=?"
        val selectionArgs = arrayOf(MediaStore.Audio.keyFor(song.title), file.absolutePath)

        val res = contentResolver.delete(collection, selection, selectionArgs)

        if (res > 0) {
            Timber.i("Deleting media store row for %s", song)
        }
    }

    private fun insertAlbumArt(albumId: Int, downloadFile: DownloadFile) {
        val contentResolver = context.contentResolver
        val uri = Uri.withAppendedPath(albumArtCollection, albumId.toString())
            ?: return
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor != null && !cursor.moveToFirst()) {
            // No album art found, add it.
            val albumArtFile = FileUtil.getAlbumArtFile(context, downloadFile.song)
            if (albumArtFile.exists()) {
                val values = ContentValues()
                values.put(MediaStore.Audio.AlbumColumns.ALBUM_ID, albumId)
                // values.put(MediaStore.MediaColumns.DATA, albumArtFile.path)
                contentResolver.insert(albumArtCollection, values)
                Timber.i("Added album art: %s", albumArtFile)
            }
            cursor.close()
        }
    }
}
