/*
 * DownloadFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import java.io.IOException
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
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
    val track: Track,
    save: Boolean
) : KoinComponent, Identifiable {
    val partialFile: String
    lateinit var completeFile: String
    val pinnedFile: String = FileUtil.getSongFile(track)
    var shouldSave = save
    internal var downloadTask: CancellableTask? = null
    var isFailed = false
    internal var retryCount = MAX_RETRIES

    val desiredBitRate: Int = Settings.maxBitRate

    var priority = 100
    var downloadPrepared = false

    @Volatile
    internal var saveWhenDone = false

    @Volatile
    var completeWhenDone = false

    val progress: MutableLiveData<Int> = MutableLiveData(0)

    // We must be able to query if the status is initialized.
    // The status is lazy because DownloadFiles are usually created in bulk, and
    // checking their status possibly means a slow SAF operation.
    val isStatusInitialized: Boolean
        get() = lazyInitialStatus.isInitialized()

    private val lazyInitialStatus: Lazy<DownloadStatus> = lazy {
        when {
            Storage.isPathExists(pinnedFile) -> {
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
        partialFile = FileUtil.getParentPath(pinnedFile) + "/" +
            FileUtil.getPartialFile(FileUtil.getNameFromPath(pinnedFile))
        completeFile = FileUtil.getParentPath(pinnedFile) + "/" +
            FileUtil.getCompleteFile(FileUtil.getNameFromPath(pinnedFile))
    }

    /**
     * Returns the effective bit rate.
     */
    fun getBitRate(): Int {
        return if (track.bitRate == null) desiredBitRate else track.bitRate!!
    }

    @Synchronized
    fun prepare() {
        // It is necessary to signal that the download will begin shortly on another thread
        // so it won't get cleaned up accidentally
        downloadPrepared = true
    }

    @Synchronized
    fun cancelDownload() {
        downloadTask?.cancel()
    }

    val completeOrSaveFile: String
        get() = if (Storage.isPathExists(pinnedFile)) {
            pinnedFile
        } else {
            completeFile
        }

    val isSaved: Boolean
        get() = Storage.isPathExists(pinnedFile)

    @get:Synchronized
    val isCompleteFileAvailable: Boolean
        get() = Storage.isPathExists(completeFile) || Storage.isPathExists(pinnedFile)

    @get:Synchronized
    val isWorkDone: Boolean
        get() = Storage.isPathExists(completeFile) && !shouldSave ||
            Storage.isPathExists(pinnedFile) || saveWhenDone || completeWhenDone

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
        Storage.delete(pinnedFile)

        status.postValue(DownloadStatus.IDLE)

        Util.scanMedia(pinnedFile)
    }

    fun unpin() {
        Timber.e("CLEANING")
        val file = Storage.getFromPath(pinnedFile) ?: return
        Storage.rename(file, completeFile)
        status.postValue(DownloadStatus.DONE)
    }

    fun cleanup(): Boolean {
        Timber.e("CLEANING")
        var ok = true
        if (Storage.isPathExists(completeFile) || Storage.isPathExists(pinnedFile)) {
            ok = Storage.delete(partialFile)
        }

        if (Storage.isPathExists(pinnedFile)) {
            ok = ok and Storage.delete(completeFile)
        }

        return ok
    }

    /**
     * Create a MediaItem instance representing the data inside this DownloadFile
     */
    val mediaItem: MediaItem by lazy {
        track.toMediaItem()
    }

    var isPlaying: Boolean = false
        get() = field
        set(isPlaying) {
            if (!isPlaying) doPendingRename()
            field = isPlaying
        }

    // Do a pending rename after the song has stopped playing
    private fun doPendingRename() {
        try {
            Timber.e("CLEANING")
            if (saveWhenDone) {
                Storage.rename(completeFile, pinnedFile)
                saveWhenDone = false
            } else if (completeWhenDone) {
                if (shouldSave) {
                    Storage.rename(partialFile, pinnedFile)
                    Util.scanMedia(pinnedFile)
                } else {
                    Storage.rename(partialFile, completeFile)
                }
                completeWhenDone = false
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to rename file %s to %s", completeFile, pinnedFile)
        }
    }

    override fun toString(): String {
        return String.format(Locale.ROOT, "DownloadFile (%s)", track)
    }

    internal fun setProgress(totalBytesCopied: Long) {
        if (track.size != null) {
            progress.postValue((totalBytesCopied * 100 / track.size!!).toInt())
        }
    }

    override fun compareTo(other: Identifiable) = compareTo(other as DownloadFile)

    fun compareTo(other: DownloadFile): Int {
        return priority.compareTo(other.priority)
    }

    override val id: String
        get() = track.id

    companion object {
        const val MAX_RETRIES = 5
    }
}

enum class DownloadStatus {
    IDLE, DOWNLOADING, RETRYING, FAILED, CANCELLED, DONE, PINNED, UNKNOWN
}
