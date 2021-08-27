package org.moire.ultrasonic.service

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.Util.isExternalStoragePresent
import org.moire.ultrasonic.util.Util.isNetworkConnected
import org.moire.ultrasonic.util.Util.getPreloadCount
import org.moire.ultrasonic.util.Util.getMaxSongs
import org.moire.ultrasonic.util.ShufflePlayBuffer
import timber.log.Timber
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.util.LRUCache
import java.util.ArrayList
import java.util.PriorityQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for maintaining the playlist and downloading
 * its items from the network to the filesystem.
 */
class Downloader(
    private val shufflePlayBuffer: ShufflePlayBuffer,
    private val externalStorageMonitor: ExternalStorageMonitor,
    private val localMediaPlayer: LocalMediaPlayer
): KoinComponent {
    val playList: MutableList<DownloadFile> = ArrayList()
    private val downloadQueue: PriorityQueue<DownloadFile> = PriorityQueue<DownloadFile>()
    private val activelyDownloading: MutableList<DownloadFile> = ArrayList()

    private val jukeboxMediaPlayer: JukeboxMediaPlayer by inject()
    
    private val downloadFileCache = LRUCache<MusicDirectory.Entry, DownloadFile>(100)

    private var executorService: ScheduledExecutorService? = null
    var downloadListUpdateRevision: Long = 0
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
        executorService!!.scheduleWithFixedDelay(downloadChecker, 5, 5, TimeUnit.SECONDS)
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

        // Check the active downloads for failures or completions
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


        // Check if need to preload more from playlist
        val preloadCount = getPreloadCount()

        // Start preloading at the current playing song
        var start = if (localMediaPlayer.currentPlaying == null) 0 else currentPlayingIndex
        if (start == -1) start = 0

        var end = (start + preloadCount).coerceAtMost(playList.size)

        // Playlist also contains played songs!!!!
        for (i in start until end) {
            val download = playList[i]

            // Set correct priority (the lower the number, the higher the priority)
            download.priority = i

            // Add file to queue if not in one of the queues already.
            if (!download.isWorkDone && !activelyDownloading.contains(download) && !downloadQueue.contains(download)) {
                downloadQueue.add(download)
            }
        }

        // Fill up active List with waiting tasks
        while (activelyDownloading.size < PARALLEL_DOWNLOADS && downloadQueue.size > 0 ) {
            val task = downloadQueue.remove()
            activelyDownloading.add(task)
            task.download()

            // The next file on the playlist is currently downloading
            // TODO: really necessary?
            if (playList.indexOf(task) == 1) {
                localMediaPlayer.setNextPlayerState(PlayerState.DOWNLOADING)
            }
        }

    }


//    fun oldStuff() {
//        // Need to download current playing?
//        if (localMediaPlayer.currentPlaying != null && localMediaPlayer.currentPlaying != currentDownloading && !localMediaPlayer.currentPlaying!!.isWorkDone) {
//            // Cancel current download, if necessary.
//            if (currentDownloading != null) {
//                currentDownloading!!.cancelDownload()
//            }
//            currentDownloading = localMediaPlayer.currentPlaying
//            currentDownloading!!.download()
//            cleanupCandidates.add(currentDownloading)
//
//            // Delete obsolete .partial and .complete files.
//            cleanup()
//            return
//        }
//
//        // Find a suitable target for download.
//        if (currentDownloading != null &&
//            !currentDownloading!!.isWorkDone &&
//            (!currentDownloading!!.isFailed || playList.isEmpty() && backgroundDownloadList.isEmpty())
//        ) {
//            cleanup()
//            return
//        }
//
//        // There is a target to download
//        currentDownloading = null
//        val n = playList.size
//        var preloaded = 0
//        if (n != 0) {
//            var start = if (localMediaPlayer.currentPlaying == null) 0 else currentPlayingIndex
//            if (start == -1) start = 0
//            var i = start
//            // Check all DownloadFiles on the playlist
//            do {
//                val downloadFile = playList[i]
//                if (!downloadFile.isWorkDone) {
//                    if (downloadFile.shouldSave() || preloaded < getPreloadCount()) {
//                        currentDownloading = downloadFile
//                        currentDownloading!!.download()
//                        cleanupCandidates.add(currentDownloading)
//                        if (i == start + 1) {
//                            // The next file on the playlist is currently downloading
//                            localMediaPlayer.setNextPlayerState(PlayerState.DOWNLOADING)
//                        }
//                        break
//                    }
//                } else if (localMediaPlayer.currentPlaying != downloadFile) {
//                    preloaded++
//                }
//                i = (i + 1) % n
//            } while (i != start)
//        }
//
//        // If the downloadList contains no work, check the backgroundDownloadList
//        if ((preloaded + 1 == n || preloaded >= getPreloadCount() || playList.isEmpty()) && backgroundDownloadList.isNotEmpty()) {
//            var i = 0
//            while (i < backgroundDownloadList.size) {
//                val downloadFile = backgroundDownloadList[i]
//                if (downloadFile.isWorkDone && (!downloadFile.shouldSave() || downloadFile.isSaved)) {
//                    scanMedia(downloadFile.completeFile)
//
//                    // Don't need to keep list like active song list
//                    backgroundDownloadList.removeAt(i)
//                    downloadListUpdateRevision++
//                    i--
//                } else if (downloadFile.isFailed && !downloadFile.shouldRetry()) {
//                    // Don't continue to attempt to download forever
//                    backgroundDownloadList.removeAt(i)
//                    downloadListUpdateRevision++
//                    i--
//                } else {
//                    currentDownloading = downloadFile
//                    currentDownloading!!.download()
//                    cleanupCandidates.add(currentDownloading)
//                    break
//                }
//                i++
//            }
//        }
//
//    }

    @get:Synchronized
    val currentPlayingIndex: Int
        get() = playList.indexOf(localMediaPlayer.currentPlaying)

    @get:Synchronized
    val downloadListDuration: Long
        get() {
            var totalDuration: Long = 0
            for (downloadFile in playList) {
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
            temp.addAll(playList)
            temp.addAll(activelyDownloading)
            temp.addAll(downloadQueue)
            return temp.distinct()
        }

    @Synchronized
    fun clearPlaylist() {
        playList.clear()

        // Cancel all active downloads with a high priority
        for (download in activelyDownloading) {
            if (download.priority < 100)
                download.cancelDownload()
        }

        downloadListUpdateRevision++
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

        downloadListUpdateRevision++
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
        playList.remove(downloadFile)
        downloadListUpdateRevision++
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
            playList.clear()
        }
        if (playNext) {
            if (autoPlay && currentPlayingIndex >= 0) {
                offset = 0
            }
            for (song in songs) {
                val downloadFile = DownloadFile(song!!, save)
                playList.add(currentPlayingIndex + offset, downloadFile)
                offset++
            }
        } else {
            for (song in songs) {
                val downloadFile = DownloadFile(song!!, save)
                playList.add(downloadFile)
            }
        }
        downloadListUpdateRevision++
        //checkDownloads()
    }

    @Synchronized
    fun downloadBackground(songs: List<MusicDirectory.Entry>, save: Boolean) {

        // Because of the priority handling we add the songs in the reverse order they
        // were requested, then it is correct in the end.
        for (song in songs.asReversed()) {
            downloadQueue.add(DownloadFile(song, save))
        }

        downloadListUpdateRevision++
        //checkDownloads()
    }

    @Synchronized
    fun shuffle() {
        playList.shuffle()

        // Move the current song to the top..
        if (localMediaPlayer.currentPlaying != null) {
            playList.remove(localMediaPlayer.currentPlaying)
            playList.add(0, localMediaPlayer.currentPlaying!!)
        }

        downloadListUpdateRevision++
    }

    @Synchronized
    fun getDownloadFileForSong(song: MusicDirectory.Entry): DownloadFile {
        for (downloadFile in playList) {
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
        val wasEmpty = playList.isEmpty()
        val revisionBefore = downloadListUpdateRevision

        // First, ensure that list is at least 20 songs long.
        val size = playList.size
        if (size < listSize) {
            for (song in shufflePlayBuffer[listSize - size]) {
                val downloadFile = DownloadFile(song, false)
                playList.add(downloadFile)
                downloadListUpdateRevision++
            }
        }
        
        val currIndex = if (localMediaPlayer.currentPlaying == null) 0 else currentPlayingIndex

        // Only shift playlist if playing song #5 or later.
        if (currIndex > 4) {
            val songsToShift = currIndex - 2
            for (song in shufflePlayBuffer[songsToShift]) {
                playList.add(DownloadFile(song, false))
                playList[0].cancelDownload()
                playList.removeAt(0)
                downloadListUpdateRevision++
            }
        }
        if (revisionBefore != downloadListUpdateRevision) {
            jukeboxMediaPlayer.updatePlaylist()
        }
        if (wasEmpty && playList.isNotEmpty()) {
            if (jukeboxMediaPlayer.isEnabled) {
                jukeboxMediaPlayer.skip(0, 0)
                localMediaPlayer.setPlayerState(PlayerState.STARTED)
            } else {
                localMediaPlayer.play(playList[0])
            }
        }
    }
    companion object {
        const val PARALLEL_DOWNLOADS = 3
    }
}
