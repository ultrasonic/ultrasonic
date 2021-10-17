package org.moire.ultrasonic.service

import android.net.wifi.WifiManager
import androidx.lifecycle.MutableLiveData
import java.util.ArrayList
import java.util.PriorityQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.util.LRUCache
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShufflePlayBuffer
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * This class is responsible for maintaining the playlist and downloading
 * its items from the network to the filesystem.
 *
 * TODO: Move away from managing the queue with scheduled checks, instead use callbacks when
 * Downloads are finished
 */
class Downloader(
    private val shufflePlayBuffer: ShufflePlayBuffer,
    private val externalStorageMonitor: ExternalStorageMonitor,
    private val localMediaPlayer: LocalMediaPlayer
) : KoinComponent {
    val playlist: MutableList<DownloadFile> = ArrayList()
    var started: Boolean = false

    private val downloadQueue: PriorityQueue<DownloadFile> = PriorityQueue<DownloadFile>()
    private val activelyDownloading: MutableList<DownloadFile> = ArrayList()

    val observableList: MutableLiveData<List<DownloadFile>> = MutableLiveData<List<DownloadFile>>()

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()

    private val downloadFileCache = LRUCache<MusicDirectory.Entry, DownloadFile>(100)

    private var executorService: ScheduledExecutorService? = null
    private var wifiLock: WifiManager.WifiLock? = null

    var playlistUpdateRevision: Long = 0
        private set

    val downloadChecker = Runnable {
        try {
            Timber.w("Checking Downloads")
            checkDownloadsInternal()
        } catch (all: Exception) {
            Timber.e(all, "checkDownloads() failed.")
        }
    }

    fun onDestroy() {
        stop()
        clearPlaylist()
        clearBackground()
        observableList.value = listOf()
        Timber.i("Downloader destroyed")
    }

    fun start() {
        started = true
        if (executorService == null) {
            executorService = Executors.newSingleThreadScheduledExecutor()
            executorService!!.scheduleWithFixedDelay(
                downloadChecker, 0L, CHECK_INTERVAL, TimeUnit.SECONDS
            )
            Timber.i("Downloader started")
        }

        if (wifiLock == null) {
            wifiLock = Util.createWifiLock(toString())
            wifiLock?.acquire()
        }
    }

    fun stop() {
        started = false
        executorService?.shutdown()
        executorService = null
        wifiLock?.release()
        wifiLock = null
        MediaPlayerService.runningInstance?.notifyDownloaderStopped()
        Timber.i("Downloader stopped")
    }

    fun checkDownloads() {
        if (executorService == null || executorService!!.isTerminated) {
            start()
        } else {
            executorService?.execute(downloadChecker)
        }
    }

    @Synchronized
    @Suppress("ComplexMethod")
    fun checkDownloadsInternal() {
        if (
            !Util.isExternalStoragePresent() ||
            !externalStorageMonitor.isExternalStorageAvailable
        ) {
            return
        }
        if (shufflePlayBuffer.isEnabled) {
            checkShufflePlay()
        }
        if (jukeboxMediaPlayer.isEnabled || !Util.isNetworkConnected()) {
            return
        }

        // Check the active downloads for failures or completions and remove them
        // Store the result in a flag to know if changes have occurred
        var listChanged = cleanupActiveDownloads()

        // Check if need to preload more from playlist
        val preloadCount = Settings.preloadCount

        // Start preloading at the current playing song
        var start = currentPlayingIndex
        if (start == -1) start = 0

        val end = (start + preloadCount).coerceAtMost(playlist.size)

        for (i in start until end) {
            val download = playlist[i]

            // Set correct priority (the lower the number, the higher the priority)
            download.priority = i

            // Add file to queue if not in one of the queues already.
            if (!download.isWorkDone &&
                !activelyDownloading.contains(download) &&
                !downloadQueue.contains(download)
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

            // The next file on the playlist is currently downloading
            if (playlist.indexOf(task) == 1) {
                localMediaPlayer.setNextPlayerState(PlayerState.DOWNLOADING)
            }
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
        observableList.postValue(downloads)
    }

    private fun startDownloadOnService(task: DownloadFile) {
        MediaPlayerService.executeOnStartedMediaPlayerService {
            task.download()
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
    val currentPlayingIndex: Int
        get() = playlist.indexOf(localMediaPlayer.currentPlaying)

    @get:Synchronized
    val downloadListDuration: Long
        get() {
            var totalDuration: Long = 0
            for (downloadFile in playlist) {
                val song = downloadFile.song
                if (!song.isDirectory) {
                    if (song.artist != null) {
                        if (song.duration != null) {
                            totalDuration += song.duration!!.toLong()
                        }
                    }
                }
            }
            return totalDuration
        }

    @get:Synchronized
    val all: List<DownloadFile>
        get() {
            val temp: MutableList<DownloadFile> = ArrayList()
            temp.addAll(activelyDownloading)
            temp.addAll(downloadQueue)
            temp.addAll(playlist)
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
                playlist.filter {
                    when (it.status.value) {
                        DownloadStatus.DOWNLOADING -> true
                        else -> false
                    }
                }
            )
            return temp.distinct().sorted()
        }

    @Synchronized
    fun clearPlaylist() {
        playlist.clear()

        // Cancel all active downloads with a high priority
        for (download in activelyDownloading) {
            if (download.priority < 100) {
                download.cancelDownload()
                activelyDownloading.remove(download)
            }
        }

        playlistUpdateRevision++
        updateLiveData()
    }

    @Synchronized
    private fun clearBackground() {
        // Clear the pending queue
        downloadQueue.clear()

        // Cancel all active downloads with a low priority
        for (download in activelyDownloading) {
            if (download.priority >= 100) {
                download.cancelDownload()
                activelyDownloading.remove(download)
            }
        }
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
    fun removeFromPlaylist(downloadFile: DownloadFile) {
        if (activelyDownloading.contains(downloadFile)) {
            downloadFile.cancelDownload()
        }
        playlist.remove(downloadFile)
        playlistUpdateRevision++
        checkDownloads()
    }

    @Synchronized
    fun addToPlaylist(
        songs: List<MusicDirectory.Entry?>,
        save: Boolean,
        autoPlay: Boolean,
        playNext: Boolean,
        newPlaylist: Boolean
    ) {
        shufflePlayBuffer.isEnabled = false
        var offset = 1
        if (songs.isEmpty()) {
            return
        }
        if (newPlaylist) {
            playlist.clear()
        }
        if (playNext) {
            if (autoPlay && currentPlayingIndex >= 0) {
                offset = 0
            }
            for (song in songs) {
                val downloadFile = DownloadFile(song!!, save)
                playlist.add(currentPlayingIndex + offset, downloadFile)
                offset++
            }
        } else {
            for (song in songs) {
                val downloadFile = DownloadFile(song!!, save)
                playlist.add(downloadFile)
            }
        }
        playlistUpdateRevision++
        checkDownloads()
    }

    @Synchronized
    fun downloadBackground(songs: List<MusicDirectory.Entry>, save: Boolean) {

        // Because of the priority handling we add the songs in the reverse order they
        // were requested, then it is correct in the end.
        for (song in songs.asReversed()) {
            downloadQueue.add(DownloadFile(song, save))
        }

        checkDownloads()
    }

    @Synchronized
    fun shuffle() {
        playlist.shuffle()

        // Move the current song to the top..
        if (localMediaPlayer.currentPlaying != null) {
            playlist.remove(localMediaPlayer.currentPlaying)
            playlist.add(0, localMediaPlayer.currentPlaying!!)
        }

        playlistUpdateRevision++
    }

    @Synchronized
    @Suppress("ReturnCount")
    fun getDownloadFileForSong(song: MusicDirectory.Entry): DownloadFile {
        for (downloadFile in playlist) {
            if (downloadFile.song == song) {
                return downloadFile
            }
        }
        for (downloadFile in activelyDownloading) {
            if (downloadFile.song == song) {
                return downloadFile
            }
        }
        for (downloadFile in downloadQueue) {
            if (downloadFile.song == song) {
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

    @Synchronized
    private fun checkShufflePlay() {
        // Get users desired random playlist size
        val listSize = Settings.maxSongs
        val wasEmpty = playlist.isEmpty()
        val revisionBefore = playlistUpdateRevision

        // First, ensure that list is at least 20 songs long.
        val size = playlist.size
        if (size < listSize) {
            for (song in shufflePlayBuffer[listSize - size]) {
                val downloadFile = DownloadFile(song, false)
                playlist.add(downloadFile)
                playlistUpdateRevision++
            }
        }

        val currIndex = if (localMediaPlayer.currentPlaying == null) 0 else currentPlayingIndex

        // Only shift playlist if playing song #5 or later.
        if (currIndex > SHUFFLE_BUFFER_LIMIT) {
            val songsToShift = currIndex - 2
            for (song in shufflePlayBuffer[songsToShift]) {
                playlist.add(DownloadFile(song, false))
                playlist[0].cancelDownload()
                playlist.removeAt(0)
                playlistUpdateRevision++
            }
        }
        if (revisionBefore != playlistUpdateRevision) {
            jukeboxMediaPlayer.updatePlaylist()
        }
        if (wasEmpty && playlist.isNotEmpty()) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.skip(0, 0)
                localMediaPlayer.setPlayerState(PlayerState.STARTED)
            } else {
                localMediaPlayer.play(playlist[0])
            }
        }
    }
    companion object {
        const val PARALLEL_DOWNLOADS = 3
        const val CHECK_INTERVAL = 5L
        const val SHUFFLE_BUFFER_LIMIT = 4
    }
}
