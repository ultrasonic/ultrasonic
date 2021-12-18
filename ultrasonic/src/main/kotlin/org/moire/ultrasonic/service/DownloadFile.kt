/*
 * DownloadFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
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
    val partialFile: String
    lateinit var completeFile: String
    val saveFile: String = FileUtil.getSongFile(song)
    var shouldSave = save
    private var downloadTask: CancellableTask? = null
    var isFailed = false
    private var retryCount = MAX_RETRIES

    private val desiredBitRate: Int = Settings.maxBitRate

    var priority = 100
    var downloadPrepared = false

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

    // We must be able to query if the status is initialized.
    // The status is lazy because DownloadFiles are usually created in bulk, and
    // checking their status possibly means a slow SAF operation.
    val isStatusInitialized: Boolean
        get() = lazyInitialStatus.isInitialized()

    private val lazyInitialStatus: Lazy<DownloadStatus> = lazy {
        when {
            Storage.isPathExists(saveFile) -> {
                DownloadStatus.PINNED
            }
            Storage.isPathExists(completeFile) -> {
                DownloadStatus.DONE
            }
            else -> {
                DownloadStatus.IDLE
            }
        }
    }

    val status: MutableLiveData<DownloadStatus> by lazy {
        MutableLiveData(lazyInitialStatus.value)
    }

    init {
        partialFile = FileUtil.getParentPath(saveFile) + "/" +
            FileUtil.getPartialFile(FileUtil.getNameFromPath(saveFile))
        completeFile = FileUtil.getParentPath(saveFile) + "/" +
            FileUtil.getCompleteFile(FileUtil.getNameFromPath(saveFile))
    }

    /**
     * Returns the effective bit rate.
     */
    fun getBitRate(): Int {
        return if (song.bitRate == null) desiredBitRate else song.bitRate!!
    }

    @Synchronized
    fun prepare() {
        // It is necessary to signal that the download will begin shortly on another thread
        // so it won't get cleaned up accidentally
        downloadPrepared = true
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
        downloadTask?.cancel()
    }

    val completeOrSaveFile: String
        get() = if (Storage.isPathExists(saveFile)) {
            saveFile
        } else {
            completeFile
        }

    val completeOrPartialFile: String
        get() = if (isCompleteFileAvailable) {
            completeOrSaveFile
        } else {
            partialFile
        }

    val isSaved: Boolean
        get() = Storage.isPathExists(saveFile)

    @get:Synchronized
    val isCompleteFileAvailable: Boolean
        get() = Storage.isPathExists(completeFile) || Storage.isPathExists(saveFile)

    @get:Synchronized
    val isWorkDone: Boolean
        get() = Storage.isPathExists(completeFile) && !shouldSave ||
            Storage.isPathExists(saveFile) || saveWhenDone || completeWhenDone

    @get:Synchronized
    val isDownloading: Boolean
        get() = downloadPrepared || (downloadTask != null && downloadTask!!.isRunning)

    @get:Synchronized
    val isDownloadCancelled: Boolean
        get() = downloadTask != null && downloadTask!!.isCancelled

    fun shouldRetry(): Boolean {
        return (retryCount > 0)
    }

    fun delete() {
        cancelDownload()
        Storage.delete(partialFile)
        Storage.delete(completeFile)
        Storage.delete(saveFile)

        status.postValue(DownloadStatus.IDLE)

        Util.scanMedia(saveFile)
    }

    fun unpin() {
        val file = Storage.getFromPath(saveFile) ?: return
        Storage.rename(file, completeFile)
        status.postValue(DownloadStatus.DONE)
    }

    fun cleanup(): Boolean {
        var ok = true
        if (Storage.isPathExists(completeFile) || Storage.isPathExists(saveFile)) {
            ok = Storage.delete(partialFile)
        }

        if (Storage.isPathExists(saveFile)) {
            ok = ok and Storage.delete(completeFile)
        }

        return ok
    }

    fun setPlaying(isPlaying: Boolean) {
        if (!isPlaying) doPendingRename()
        this.isPlaying = isPlaying
    }

    // Do a pending rename after the song has stopped playing
    private fun doPendingRename() {
        try {
            if (saveWhenDone) {
                Storage.rename(completeFile, saveFile)
                saveWhenDone = false
            } else if (completeWhenDone) {
                if (shouldSave) {
                    Storage.rename(partialFile, saveFile)
                    Util.scanMedia(saveFile)
                } else {
                    Storage.rename(partialFile, completeFile)
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

            downloadPrepared = false
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                if (Storage.isPathExists(saveFile)) {
                    Timber.i("%s already exists. Skipping.", saveFile)
                    status.postValue(DownloadStatus.PINNED)
                    return
                }

                if (Storage.isPathExists(completeFile)) {
                    var newStatus: DownloadStatus = DownloadStatus.DONE
                    if (shouldSave) {
                        if (isPlaying) {
                            saveWhenDone = true
                        } else {
                            Storage.rename(completeFile, saveFile)
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
                val fileLength = Storage.getFromPath(partialFile)?.length ?: 0

                needsDownloading = (
                    desiredBitRate == 0 || duration == null || duration == 0 || fileLength == 0L
                    )

                if (needsDownloading) {
                    // Attempt partial HTTP GET, appending to the file if it exists.
                    val (inStream, isPartial) = musicService.getDownloadInputStream(
                        song, fileLength, desiredBitRate, shouldSave
                    )

                    inputStream = inStream

                    if (isPartial) {
                        Timber.i("Executed partial HTTP GET, skipping %d bytes", fileLength)
                    }

                    outputStream = Storage.getOrCreateFileFromPath(partialFile)
                        .getFileOutputStream(isPartial)

                    val len = inputStream.copyTo(outputStream) { totalBytesCopied ->
                        setProgress(totalBytesCopied)
                    }

                    Timber.i("Downloaded %d bytes to %s", len, partialFile)

                    inputStream.close()
                    outputStream.flush()
                    outputStream.close()

                    if (isCancelled) {
                        status.postValue(DownloadStatus.CANCELLED)
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
                        Storage.rename(partialFile, saveFile)
                        status.postValue(DownloadStatus.PINNED)
                        Util.scanMedia(saveFile)
                    } else {
                        Storage.rename(partialFile, completeFile)
                        status.postValue(DownloadStatus.DONE)
                    }
                }
            } catch (all: Exception) {
                outputStream.safeClose()
                Storage.delete(completeFile)
                Storage.delete(saveFile)
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
                inputStream.safeClose()
                outputStream.safeClose()
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
    IDLE, DOWNLOADING, RETRYING, FAILED, CANCELLED, DONE, PINNED, UNKNOWN
}
