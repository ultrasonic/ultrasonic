package org.moire.ultrasonic.service

import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.PriorityQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.playback.LegacyPlaylistManager
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.CancellableTask
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.LRUCache
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

/**
 * This class is responsible for maintaining the playlist and downloading
 * its items from the network to the filesystem.
 *
 * TODO: Move entirely to subclass the Media3.DownloadService
 */
class Downloader(
    private val storageMonitor: ExternalStorageMonitor,
    private val legacyPlaylistManager: LegacyPlaylistManager,
) : KoinComponent {

    // Dependencies
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val mediaController: MediaPlayerController by inject()

    var started: Boolean = false
    var shouldStop: Boolean = false

    private val downloadQueue = PriorityQueue<DownloadFile>()
    private val activelyDownloading = mutableListOf<DownloadFile>()

    // The generic list models expect a LiveData, so even though we are using Rx for many events
    // surrounding playback the list of Downloads is published as LiveData.
    val observableDownloads = MutableLiveData<List<DownloadFile>>()

    // This cache helps us to avoid creating duplicate DownloadFile instances when showing Entries
    private val downloadFileCache = LRUCache<Track, DownloadFile>(500)

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var wifiLock: WifiManager.WifiLock? = null

    private var backgroundPriorityCounter = 100

    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private var downloadChecker = object : Runnable {
        override fun run() {
            try {
                Timber.w("Checking Downloads")
                checkDownloadsInternal()
            } catch (all: Exception) {
                Timber.e(all, "checkDownloads() failed.")
            } finally {
                if (!shouldStop) {
                    Handler(Looper.getMainLooper()).postDelayed(this, CHECK_INTERVAL)
                } else {
                    shouldStop = false
                }
            }
        }
    }

    fun onDestroy() {
        stop()
        rxBusSubscription.dispose()
        clearBackground()
        observableDownloads.value = listOf()
        Timber.i("Downloader destroyed")
    }

    @Synchronized
    fun start() {
        started = true

        // Start our loop
        handler.postDelayed(downloadChecker, 100)

        if (wifiLock == null) {
            wifiLock = Util.createWifiLock(toString())
            wifiLock?.acquire()
        }

        // Check downloads if the playlist changed
        rxBusSubscription += RxBus.playlistObservable.subscribe {
            checkDownloads()
        }
    }

    fun stop() {
        started = false
        shouldStop = true
        wifiLock?.release()
        wifiLock = null
        DownloadService.runningInstance?.notifyDownloaderStopped()
        Timber.i("Downloader stopped")
    }

    fun checkDownloads() {
        if (!started) {
            start()
        } else {
            try {
                handler.postDelayed(downloadChecker, 100)
            } catch (all: Exception) {
                Timber.w(
                    all,
                    "checkDownloads() can't run, maybe the Downloader is shutting down..."
                )
            }
        }
    }

    @Suppress("ComplexMethod", "ComplexCondition")
    @Synchronized
    fun checkDownloadsInternal() {
        if (!Util.isExternalStoragePresent() || !storageMonitor.isExternalStorageAvailable) {
            return
        }

        if (legacyPlaylistManager.jukeboxMediaPlayer.isEnabled || !Util.isNetworkConnected()) {
            return
        }

        Timber.v("Downloader checkDownloadsInternal checking downloads")

        // Check the active downloads for failures or completions and remove them
        // Store the result in a flag to know if changes have occurred
        var listChanged = cleanupActiveDownloads()

        // Check if need to preload more from playlist
        val preloadCount = Settings.preloadCount

        // Start preloading at the current playing song
        var start = mediaController.currentMediaItemIndex

        if (start == -1) start = 0

        val end = (start + preloadCount).coerceAtMost(mediaController.mediaItemCount)

        for (i in start until end) {
            val download = legacyPlaylistManager.playlist[i]

            // Set correct priority (the lower the number, the higher the priority)
            download.priority = i

            // Add file to queue if not in one of the queues already.
            if (!download.isWorkDone &&
                !activelyDownloading.contains(download) &&
                !downloadQueue.contains(download) &&
                download.shouldRetry()
            ) {
                listChanged = true
                downloadQueue.add(download)
            }
        }

        // Fill up active List with waiting tasks
        while (activelyDownloading.size < PARALLEL_DOWNLOADS && downloadQueue.size > 0) {
            val task = downloadQueue.remove()
            activelyDownloading.add(task)
            startDownloadOnService(task)

            listChanged = true
        }

        // Stop Executor service when done downloading
        if (activelyDownloading.size == 0) {
            stop()
        }

        if (listChanged) {
            updateLiveData()
        }
    }

    private fun updateLiveData() {
        observableDownloads.postValue(downloads)
    }

    private fun startDownloadOnService(file: DownloadFile) {
        if (file.isDownloading) return
        file.prepare()
        DownloadService.executeOnStartedMediaPlayerService {
            FileUtil.createDirectoryForParent(file.pinnedFile)
            file.isFailed = false
            file.downloadTask = DownloadTask(file)
            file.downloadTask!!.start()
        }
    }

    /**
     * Return true if modifications were made
     */
    private fun cleanupActiveDownloads(): Boolean {
        val oldSize = activelyDownloading.size

        activelyDownloading.retainAll {
            when {
                it.isDownloading -> true
                it.isFailed && it.shouldRetry() -> {
                    // Add it back to queue
                    downloadQueue.add(it)
                    false
                }
                else -> {
                    it.cleanup()
                    false
                }
            }
        }

        return (oldSize != activelyDownloading.size)
    }

    @get:Synchronized
    val all: List<DownloadFile>
        get() {
            val temp: MutableList<DownloadFile> = ArrayList()
            temp.addAll(activelyDownloading)
            temp.addAll(downloadQueue)
            temp.addAll(legacyPlaylistManager.playlist)
            return temp.distinct().sorted()
        }

    /*
    * Returns a list of all DownloadFiles that are currently downloading or waiting for download,
    * including undownloaded files from the playlist.
     */
    @get:Synchronized
    val downloads: List<DownloadFile>
        get() {
            val temp: MutableList<DownloadFile> = ArrayList()
            temp.addAll(activelyDownloading)
            temp.addAll(downloadQueue)
            temp.addAll(
                legacyPlaylistManager.playlist.filter {
                    if (!it.isStatusInitialized) false
                    else when (it.status.value) {
                        DownloadStatus.DOWNLOADING -> true
                        else -> false
                    }
                }
            )
            return temp.distinct().sorted()
        }

    @Synchronized
    fun clearDownloadFileCache() {
        downloadFileCache.clear()
    }

    @Synchronized
    fun clearBackground() {
        // Clear the pending queue
        downloadQueue.clear()

        // Cancel all active downloads with a low priority
        for (download in activelyDownloading) {
            if (download.priority >= 100) {
                download.cancelDownload()
                activelyDownloading.remove(download)
            }
        }

        backgroundPriorityCounter = 100
    }

    @Synchronized
    fun clearActiveDownloads() {
        // Cancel all active downloads
        for (download in activelyDownloading) {
            download.cancelDownload()
        }
        activelyDownloading.clear()
        updateLiveData()
    }

    @Synchronized
    fun downloadBackground(songs: List<Track>, save: Boolean) {

        // By using the counter we ensure that the songs are added in the correct order
        for (song in songs) {
            val file = song.getDownloadFile()
            file.shouldSave = save
            if (!file.isDownloading) {
                file.priority = backgroundPriorityCounter++
                downloadQueue.add(file)
            }
        }

        checkDownloads()
    }

    @Synchronized
    @Suppress("ReturnCount")
    fun getDownloadFileForSong(song: Track): DownloadFile {
        for (downloadFile in legacyPlaylistManager.playlist) {
            if (downloadFile.track == song) {
                return downloadFile
            }
        }
        for (downloadFile in activelyDownloading) {
            if (downloadFile.track == song) {
                return downloadFile
            }
        }
        for (downloadFile in downloadQueue) {
            if (downloadFile.track == song) {
                return downloadFile
            }
        }
        var downloadFile = downloadFileCache[song]
        if (downloadFile == null) {
            downloadFile = DownloadFile(song, false)
            downloadFileCache.put(song, downloadFile)
        }
        return downloadFile
    }

    companion object {
        const val PARALLEL_DOWNLOADS = 3
        const val CHECK_INTERVAL = 5000L
    }

    /**
     * Extension function
     * Gathers the download file for a given song, and modifies shouldSave if provided.
     */
    private fun Track.getDownloadFile(save: Boolean? = null): DownloadFile {
        return getDownloadFileForSong(this).apply {
            if (save != null) this.shouldSave = save
        }
    }

    private inner class DownloadTask(private val downloadFile: DownloadFile) : CancellableTask() {
        val musicService = MusicServiceFactory.getMusicService()

        @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
        override fun execute() {

            downloadFile.downloadPrepared = false
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                if (Storage.isPathExists(downloadFile.pinnedFile)) {
                    Timber.i("%s already exists. Skipping.", downloadFile.pinnedFile)
                    downloadFile.status.postValue(DownloadStatus.PINNED)
                    return
                }

                if (Storage.isPathExists(downloadFile.completeFile)) {
                    var newStatus: DownloadStatus = DownloadStatus.DONE
                    if (downloadFile.shouldSave) {
                        if (downloadFile.isPlaying) {
                            downloadFile.saveWhenDone = true
                        } else {
                            Storage.rename(
                                downloadFile.completeFile,
                                downloadFile.pinnedFile
                            )
                            newStatus = DownloadStatus.PINNED
                        }
                    } else {
                        Timber.i(
                            "%s already exists. Skipping.",
                            downloadFile.completeFile
                        )
                    }
                    downloadFile.status.postValue(newStatus)
                    return
                }

                downloadFile.status.postValue(DownloadStatus.DOWNLOADING)

                // Some devices seem to throw error on partial file which doesn't exist
                val needsDownloading: Boolean
                val duration = downloadFile.track.duration
                val fileLength = Storage.getFromPath(downloadFile.partialFile)?.length ?: 0

                needsDownloading = (
                        downloadFile.desiredBitRate == 0 ||
                                duration == null ||
                                duration == 0 ||
                                fileLength == 0L
                        )

                if (needsDownloading) {
                    // Attempt partial HTTP GET, appending to the file if it exists.
                    val (inStream, isPartial) = musicService.getDownloadInputStream(
                        downloadFile.track, fileLength,
                        downloadFile.desiredBitRate,
                        downloadFile.shouldSave
                    )

                    inputStream = inStream

                    if (isPartial) {
                        Timber.i("Executed partial HTTP GET, skipping %d bytes", fileLength)
                    }

                    outputStream = Storage.getOrCreateFileFromPath(downloadFile.partialFile)
                        .getFileOutputStream(isPartial)

                    val len = inputStream.copyTo(outputStream) { totalBytesCopied ->
                        downloadFile.setProgress(totalBytesCopied)
                    }

                    Timber.i("Downloaded %d bytes to %s", len, downloadFile.partialFile)

                    inputStream.close()
                    outputStream.flush()
                    outputStream.close()

                    if (isCancelled) {
                        downloadFile.status.postValue(DownloadStatus.CANCELLED)
                        throw RuntimeException(
                            String.format(
                                Locale.ROOT, "Download of '%s' was cancelled",
                                downloadFile.track
                            )
                        )
                    }

                    if (downloadFile.track.artistId != null) {
                        cacheMetadata(downloadFile.track.artistId!!)
                    }

                    downloadAndSaveCoverArt()
                }

                if (downloadFile.isPlaying) {
                    downloadFile.completeWhenDone = true
                } else {
                    if (downloadFile.shouldSave) {
                        Storage.rename(
                            downloadFile.partialFile,
                            downloadFile.pinnedFile
                        )
                        downloadFile.status.postValue(DownloadStatus.PINNED)
                        Util.scanMedia(downloadFile.pinnedFile)
                    } else {
                        Storage.rename(
                            downloadFile.partialFile,
                            downloadFile.completeFile
                        )
                        downloadFile.status.postValue(DownloadStatus.DONE)
                    }
                }
            } catch (all: Exception) {
                outputStream.safeClose()
                Storage.delete(downloadFile.completeFile)
                Storage.delete(downloadFile.pinnedFile)
                if (!isCancelled) {
                    downloadFile.isFailed = true
                    if (downloadFile.retryCount > 1) {
                        downloadFile.status.postValue(DownloadStatus.RETRYING)
                        --downloadFile.retryCount
                    } else if (downloadFile.retryCount == 1) {
                        downloadFile.status.postValue(DownloadStatus.FAILED)
                        --downloadFile.retryCount
                    }
                    Timber.w(all, "Failed to download '%s'.", downloadFile.track)
                }
            } finally {
                inputStream.safeClose()
                outputStream.safeClose()
                CacheCleaner().cleanSpace()
                checkDownloads()
            }
        }

        override fun toString(): String {
            return String.format(Locale.ROOT, "DownloadTask (%s)", downloadFile.track)
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
                if (!TextUtils.isEmpty(downloadFile.track.coverArt)) {
                    // Download the largest size that we can display in the UI
                    imageLoaderProvider.getImageLoader().cacheCoverArt(downloadFile.track)
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
}
