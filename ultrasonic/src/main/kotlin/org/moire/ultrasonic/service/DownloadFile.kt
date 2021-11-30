/*
 * DownloadFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * This class represents a single Song or Video that can be downloaded.
 *
 * Terminology:
 * PinnedFile: A "pinned" song. Will stay in cache permanently
 * CompleteFile: A "downloaded" song. Will be quicker to be deleted if the cache is full
 *
 */
class DownloadFile(
    val song: MusicDirectory.Entry,
    save: Boolean
) : KoinComponent, Identifiable {
    var shouldSave = save
    val partialFile: File
    val completeFile: File
    private val saveFile: File = FileUtil.getSongFile(song)
    private var downloadTask: CancellableTask? = null
    var isFailed = false
    private var retryCount = MAX_RETRIES

    private val desiredBitRate: Int = Settings.maxBitRate

    var priority = 100

    @Volatile
    private var isPlaying = false

    @Volatile
    private var saveWhenDone = false

    @Volatile
    private var completeWhenDone = false

    private val downloader: Downloader by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    val progress: MutableLiveData<Int> = MutableLiveData(0)
    val status: MutableLiveData<DownloadStatus>

    init {
        val state: DownloadStatus

        partialFile = File(saveFile.parent, FileUtil.getPartialFile(saveFile.name))
        completeFile = File(saveFile.parent, FileUtil.getCompleteFile(saveFile.name))

        when {
            saveFile.exists() -> {
                state = DownloadStatus.PINNED
            }
            completeFile.exists() -> {
                state = DownloadStatus.DONE
            }
            else -> {
                state = DownloadStatus.IDLE
            }
        }

        status = MutableLiveData(state)
    }

    /**
     * Returns the effective bit rate.
     */
    fun getBitRate(): Int {
        return if (song.bitRate == null) desiredBitRate else song.bitRate!!
    }

    @Synchronized
    fun download() {
        FileUtil.createDirectoryForParent(saveFile)
        isFailed = false
        downloadTask = DownloadTask()
        downloadTask!!.start()
    }

    @Synchronized
    fun cancelDownload() {
        if (downloadTask != null) {
            downloadTask!!.cancel()
        }
    }

    val completeOrSaveFile: File
        get() = if (saveFile.exists()) {
            saveFile
        } else {
            completeFile
        }

    val completeOrPartialFile: File
        get() = if (isCompleteFileAvailable) {
            completeOrSaveFile
        } else {
            partialFile
        }

    val isSaved: Boolean
        get() = saveFile.exists()

    @get:Synchronized
    val isCompleteFileAvailable: Boolean
        get() = saveFile.exists() || completeFile.exists()

    @get:Synchronized
    val isWorkDone: Boolean
        get() = saveFile.exists() || completeFile.exists() && !shouldSave ||
            saveWhenDone || completeWhenDone

    @get:Synchronized
    val isDownloading: Boolean
        get() = downloadTask != null && downloadTask!!.isRunning

    @get:Synchronized
    val isDownloadCancelled: Boolean
        get() = downloadTask != null && downloadTask!!.isCancelled

    fun shouldRetry(): Boolean {
        return (retryCount > 0)
    }

    fun delete() {
        cancelDownload()
        Util.delete(partialFile)
        Util.delete(completeFile)
        Util.delete(saveFile)

        status.postValue(DownloadStatus.IDLE)

        Util.scanMedia(saveFile)
    }

    fun unpin() {
        if (saveFile.exists()) {
            if (saveFile.renameTo(completeFile)) {
                status.postValue(DownloadStatus.DONE)
            } else {
                Timber.w(
                    "Renaming file failed. Original file: %s; Rename to: %s",
                    saveFile.name, completeFile.name
                )
            }
        }
    }

    fun cleanup(): Boolean {
        var ok = true
        if (completeFile.exists() || saveFile.exists()) {
            ok = Util.delete(partialFile)
        }

        if (saveFile.exists()) {
            ok = ok and Util.delete(completeFile)
        }

        return ok
    }

    // In support of LRU caching.
    fun updateModificationDate() {
        updateModificationDate(saveFile)
        updateModificationDate(partialFile)
        updateModificationDate(completeFile)
    }

    fun setPlaying(isPlaying: Boolean) {
        if (!isPlaying) doPendingRename()
        this.isPlaying = isPlaying
    }

    // Do a pending rename after the song has stopped playing
    private fun doPendingRename() {
        try {
            if (saveWhenDone) {
                Util.renameFile(completeFile, saveFile)
                saveWhenDone = false
            } else if (completeWhenDone) {
                if (shouldSave) {
                    Util.renameFile(partialFile, saveFile)
                    Util.scanMedia(saveFile)
                } else {
                    Util.renameFile(partialFile, completeFile)
                }
                completeWhenDone = false
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to rename file %s to %s", completeFile, saveFile)
        }
    }

    override fun toString(): String {
        return String.format("DownloadFile (%s)", song)
    }

    private inner class DownloadTask : CancellableTask() {
        val musicService = getMusicService()

        override fun execute() {

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                if (saveFile.exists()) {
                    Timber.i("%s already exists. Skipping.", saveFile)
                    status.postValue(DownloadStatus.PINNED)
                    return
                }

                if (completeFile.exists()) {
                    var newStatus: DownloadStatus = DownloadStatus.DONE
                    if (shouldSave) {
                        if (isPlaying) {
                            saveWhenDone = true
                        } else {
                            Util.renameFile(completeFile, saveFile)
                            newStatus = DownloadStatus.PINNED
                        }
                    } else {
                        Timber.i("%s already exists. Skipping.", completeFile)
                    }
                    status.postValue(newStatus)
                    return
                }

                status.postValue(DownloadStatus.DOWNLOADING)

                // Some devices seem to throw error on partial file which doesn't exist
                val needsDownloading: Boolean
                val duration = song.duration
                var fileLength: Long = 0

                if (!partialFile.exists()) {
                    fileLength = partialFile.length()
                }

                needsDownloading = (
                    desiredBitRate == 0 || duration == null ||
                        duration == 0 || fileLength == 0L
                    )

                if (needsDownloading) {
                    // Attempt partial HTTP GET, appending to the file if it exists.
                    val (inStream, partial) = musicService.getDownloadInputStream(
                        song, partialFile.length(), desiredBitRate, shouldSave
                    )

                    inputStream = inStream

                    if (partial) {
                        Timber.i(
                            "Executed partial HTTP GET, skipping %d bytes",
                            partialFile.length()
                        )
                    }

                    outputStream = FileOutputStream(partialFile, partial)

                    val len = inputStream.copyTo(outputStream) { totalBytesCopied ->
                        setProgress(totalBytesCopied)
                    }

                    Timber.i("Downloaded %d bytes to %s", len, partialFile)

                    inputStream.close()
                    outputStream.flush()
                    outputStream.close()

                    if (isCancelled) {
                        status.postValue(DownloadStatus.ABORTED)
                        throw Exception(String.format("Download of '%s' was cancelled", song))
                    }

                    if (song.artistId != null) {
                        cacheMetadata(song.artistId!!)
                    }

                    downloadAndSaveCoverArt()
                }

                if (isPlaying) {
                    completeWhenDone = true
                } else {
                    if (shouldSave) {
                        Util.renameFile(partialFile, saveFile)
                        status.postValue(DownloadStatus.PINNED)
                        Util.scanMedia(saveFile)
                    } else {
                        Util.renameFile(partialFile, completeFile)
                        status.postValue(DownloadStatus.DONE)
                    }
                }
            } catch (all: Exception) {
                Util.close(outputStream)
                Util.delete(completeFile)
                Util.delete(saveFile)
                if (!isCancelled) {
                    isFailed = true
                    if (retryCount > 1) {
                        status.postValue(DownloadStatus.RETRYING)
                        --retryCount
                    } else if (retryCount == 1) {
                        status.postValue(DownloadStatus.FAILED)
                        --retryCount
                    }
                    Timber.w(all, "Failed to download '%s'.", song)
                }
            } finally {
                Util.close(inputStream)
                Util.close(outputStream)
                CacheCleaner().cleanSpace()
                downloader.checkDownloads()
            }
        }

        override fun toString(): String {
            return String.format("DownloadTask (%s)", song)
        }

        private fun cacheMetadata(artistId: String) {
            // TODO: Right now it's caching the track artist.
            // Once the albums are cached in db, we should retrieve the album,
            // and then cache the album artist.
            if (artistId.isEmpty()) return
            var artist: Artist? =
                activeServerProvider.getActiveMetaDatabase().artistsDao().get(artistId)

            // If we are downloading a new album, and the user has not visited the Artists list
            // recently, then the artist won't be in the database.
            if (artist == null) {
                val artists: List<Artist> = musicService.getArtists(true)
                artist = artists.find {
                    it.id == artistId
                }
            }

            // If we have found an artist, catch it.
            if (artist != null) {
                activeServerProvider.offlineMetaDatabase.artistsDao().insert(artist)
            }
        }

        private fun downloadAndSaveCoverArt() {
            try {
                if (!TextUtils.isEmpty(song.coverArt)) {
                    // Download the largest size that we can display in the UI
                    imageLoaderProvider.getImageLoader().cacheCoverArt(song)
                }
            } catch (all: Exception) {
                Timber.e(all, "Failed to get cover art.")
            }
        }

        @Throws(IOException::class)
        fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytesCopied: Long) -> Any): Long {
            var bytesCopied: Long = 0
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = read(buffer)
            while (!isCancelled && bytes >= 0) {
                out.write(buffer, 0, bytes)
                bytesCopied += bytes
                onCopy(bytesCopied)
                bytes = read(buffer)
            }
            return bytesCopied
        }
    }

    private fun setProgress(totalBytesCopied: Long) {
        if (song.size != null) {
            progress.postValue((totalBytesCopied * 100 / song.size!!).toInt())
        }
    }

    private fun updateModificationDate(file: File) {
        if (file.exists()) {
            val ok = file.setLastModified(System.currentTimeMillis())
            if (!ok) {
                Timber.i(
                    "Failed to set last-modified date on %s, trying alternate method",
                    file
                )
                try {
                    // Try alternate method to update last modified date to current time
                    // Found at https://code.google.com/p/android/issues/detail?id=18624
                    // According to the bug, this was fixed in Android 8.0 (API 26)
                    val raf = RandomAccessFile(file, "rw")
                    val length = raf.length()
                    raf.setLength(length + 1)
                    raf.setLength(length)
                    raf.close()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to set last-modified date on %s", file)
                }
            }
        }
    }

    override fun compareTo(other: Identifiable) = compareTo(other as DownloadFile)

    fun compareTo(other: DownloadFile): Int {
        return priority.compareTo(other.priority)
    }

    override val id: String
        get() = song.id

    companion object {
        const val MAX_RETRIES = 5
    }
}

enum class DownloadStatus {
    IDLE, DOWNLOADING, RETRYING, FAILED, ABORTED, DONE, PINNED, UNKNOWN
}
