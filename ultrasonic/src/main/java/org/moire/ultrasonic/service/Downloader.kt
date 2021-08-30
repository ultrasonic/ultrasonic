package org.moire.ultrasonic.service

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
import org.moire.ultrasonic.util.ShufflePlayBuffer
import org.moire.ultrasonic.util.Util.getMaxSongs
import org.moire.ultrasonic.util.Util.getPreloadCount
import org.moire.ultrasonic.util.Util.isExternalStoragePresent
import org.moire.ultrasonic.util.Util.isNetworkConnected
import timber.log.Timber

/**
 * This class is responsible for maintaining the playlist and downloading
 * its items from the network to the filesystem.
 *
 * TODO: Implement LiveData
 * TODO: Move away from managing the queue with scheduled checks, instead use callbacks when
 * Downloads are finished
 */
class Downloader(
    private val shufflePlayBuffer: ShufflePlayBuffer,
    private val externalStorageMonitor: ExternalStorageMonitor,
    private val localMediaPlayer: LocalMediaPlayer
) : KoinComponent {
    val playlist: MutableList<DownloadFile> = ArrayList()
    private val downloadQueue: PriorityQueue<DownloadFile> = PriorityQueue<DownloadFile>()
    private val activelyDownloading: MutableList<DownloadFile> = ArrayList()

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()

    private val downloadFileCache = LRUCache<MusicDirectory.Entry, DownloadFile>(100)

    private var executorService: ScheduledExecutorService? = null

    var playlistUpdateRevision: Long = 0
        private set

    val downloadChecker = Runnable {
        try {
            Timber.w("checking Downloads")
            checkDownloadsInternal()
        } catch (all: Exception) {
            Timber.e(all, "checkDownloads() failed.")
        }
    }

    fun onCreate() {
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService!!.scheduleWithFixedDelay(
            downloadChecker, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.SECONDS
        )
        Timber.i("Downloader created")
    }

    fun onDestroy() {
        stop()
        clearPlaylist()
        clearBackground()
        Timber.i("Downloader destroyed")
    }

    fun stop() {
        if (executorService != null) executorService!!.shutdown()
        Timber.i("Downloader stopped")
    }

    fun checkDownloads() {
        executorService?.execute(downloadChecker)
    }

    @Synchronized
    fun checkDownloadsInternal() {
        if (!isExternalStoragePresent() || !externalStorageMonitor.isExternalStorageAvailable) {
            return
        }
        if (shufflePlayBuffer.isEnabled) {
            checkShufflePlay()
        }
        if (jukeboxMediaPlayer.isEnabled || !isNetworkConnected()) {
            return
        }

        // Check the active downloads for failures or completions and remove them
        cleanupActiveDownloads()

        // Check if need to preload more from playlist
        val preloadCount = getPreloadCount()

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
        }
    }

    private fun startDownloadOnService(task: DownloadFile) {
        MediaPlayerService.executeOnStartedMediaPlayerService {
            task.download()
        }
    }

    private fun cleanupActiveDownloads() {
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
    val downloads: List<DownloadFile?>
        get() {
            val temp: MutableList<DownloadFile?> = ArrayList()
            temp.addAll(playlist)
            temp.addAll(activelyDownloading)
            temp.addAll(downloadQueue)
            return temp.distinct()
        }

    @Synchronized
    fun clearPlaylist() {
        playlist.clear()

        // Cancel all active downloads with a high priority
        for (download in activelyDownloading) {
            if (download.priority < 100)
                download.cancelDownload()
        }

        playlistUpdateRevision++
    }

    @Synchronized
    private fun clearBackground() {
        // Clear the pending queue
        downloadQueue.clear()

        // Cancel all active downloads with a low priority
        for (download in activelyDownloading) {
            if (download.priority >= 100)
                download.cancelDownload()
        }
    }

    @Synchronized
    fun clearActiveDownloads() {
        // Cancel all active downloads with a low priority
        for (download in activelyDownloading) {
            download.cancelDownload()
        }
    }

    @Synchronized
    fun removeFromPlaylist(downloadFile: DownloadFile) {
        if (activelyDownloading.contains(downloadFile)) {
            downloadFile.cancelDownload()
        }
        playlist.remove(downloadFile)
        playlistUpdateRevision++
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
        val listSize = getMaxSongs()
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
