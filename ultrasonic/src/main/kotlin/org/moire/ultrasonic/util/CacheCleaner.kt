package org.moire.ultrasonic.util

import android.system.Os
import java.util.ArrayList
import java.util.HashSet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.FileUtil.delete
import org.moire.ultrasonic.util.FileUtil.getAlbumArtFile
import org.moire.ultrasonic.util.FileUtil.getPlaylistDirectory
import org.moire.ultrasonic.util.FileUtil.getPlaylistFile
import org.moire.ultrasonic.util.FileUtil.listFiles
import org.moire.ultrasonic.util.FileUtil.musicDirectory
import org.moire.ultrasonic.util.Settings.cacheSizeMB
import org.moire.ultrasonic.util.Util.formatBytes
import timber.log.Timber

/**
 * Responsible for cleaning up files from the offline download cache on the filesystem.
 */
class CacheCleaner : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private fun exceptionHandler(tag: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            Timber.w(exception, "Exception in CacheCleaner.$tag")
        }
    }

    // Cache cleaning shouldn't run concurrently, as it is started after every completed download
    // TODO serializing and throttling these is an ideal task for Rx
    fun clean() {
        if (cleaning) return
        synchronized(lock) {
            if (cleaning) return
            cleaning = true
            launch(exceptionHandler("clean")) {
                backgroundCleanup()
            }
        }
    }

    fun cleanSpace() {
        if (spaceCleaning) return
        synchronized(lock) {
            if (spaceCleaning) return
            spaceCleaning = true
            launch(exceptionHandler("cleanSpace")) {
                backgroundSpaceCleanup()
            }
        }
    }

    fun cleanPlaylists(playlists: List<Playlist>) {
        if (playlistCleaning) return
        synchronized(lock) {
            if (playlistCleaning) return
            playlistCleaning = true
            launch(exceptionHandler("cleanPlaylists")) {
                backgroundPlaylistsCleanup(playlists)
            }
        }
    }

    private fun backgroundCleanup() {
        try {
            val files: MutableList<AbstractFile> = ArrayList()
            val dirs: MutableList<AbstractFile> = ArrayList()

            findCandidatesForDeletion(musicDirectory, files, dirs)
            sortByAscendingModificationTime(files)
            val filesToNotDelete = findFilesToNotDelete()

            deleteFiles(files, filesToNotDelete, getMinimumDelete(files), true)
            deleteEmptyDirs(dirs, filesToNotDelete)
        } catch (all: RuntimeException) {
            Timber.e(all, "Error in cache cleaning.")
        } finally {
            cleaning = false
        }
    }

    private fun backgroundSpaceCleanup() {
        try {
            val files: MutableList<AbstractFile> = ArrayList()
            val dirs: MutableList<AbstractFile> = ArrayList()

            findCandidatesForDeletion(musicDirectory, files, dirs)

            val bytesToDelete = getMinimumDelete(files)
            if (bytesToDelete > 0L) {
                sortByAscendingModificationTime(files)
                val filesToNotDelete = findFilesToNotDelete()
                deleteFiles(files, filesToNotDelete, bytesToDelete, false)
            }
        } catch (all: RuntimeException) {
            Timber.e(all, "Error in cache cleaning.")
        } finally {
            spaceCleaning = false
        }
    }

    private fun backgroundPlaylistsCleanup(vararg params: List<Playlist>) {
        try {
            val activeServerProvider = inject<ActiveServerProvider>(
                ActiveServerProvider::class.java
            )

            val server = activeServerProvider.value.getActiveServer().name
            val playlistFiles = listFiles(getPlaylistDirectory(server))
            val playlists = params[0]

            for ((_, name) in playlists) {
                playlistFiles.remove(getPlaylistFile(server, name))
            }

            for (playlist in playlistFiles) {
                playlist.delete()
            }
        } catch (all: RuntimeException) {
            Timber.e(all, "Error in playlist cache cleaning.")
        } finally {
            playlistCleaning = false
        }
    }

    companion object {
        private val lock = Object()
        private var cleaning = false
        private var spaceCleaning = false
        private var playlistCleaning = false

        private const val MIN_FREE_SPACE = 500 * 1024L * 1024L
        private fun deleteEmptyDirs(dirs: Iterable<AbstractFile>, doNotDelete: Collection<String>) {
            for (dir in dirs) {
                if (doNotDelete.contains(dir.path)) continue

                var children = dir.listFiles()
                // No songs left in the folder
                if (children.size == 1 && children[0].path == getAlbumArtFile(dir.path)) {
                    // Delete Artwork files
                    delete(getAlbumArtFile(dir.path))
                    children = dir.listFiles()
                }

                // Delete empty directory
                if (children.isEmpty()) {
                    delete(dir.path)
                }
            }
        }

        private fun getMinimumDelete(files: List<AbstractFile>): Long {
            if (files.isEmpty()) return 0L

            val cacheSizeBytes = cacheSizeMB * 1024L * 1024L
            var bytesUsedBySubsonic = 0L

            for (file in files) {
                bytesUsedBySubsonic += file.length
            }

            // Ensure that file system is not more than 95% full.
            val bytesUsedFs: Long
            val minFsAvailability: Long
            val bytesTotalFs: Long
            val bytesAvailableFs: Long

            val descriptor = files[0].getDocumentFileDescriptor("r")!!
            val stat = Os.fstatvfs(descriptor.fileDescriptor)
            bytesTotalFs = stat.f_blocks * stat.f_bsize
            bytesAvailableFs = stat.f_bfree * stat.f_bsize
            bytesUsedFs = bytesTotalFs - bytesAvailableFs
            minFsAvailability = bytesTotalFs - MIN_FREE_SPACE
            descriptor.close()

            val bytesToDeleteCacheLimit = (bytesUsedBySubsonic - cacheSizeBytes).coerceAtLeast(0L)
            val bytesToDeleteFsLimit = (bytesUsedFs - minFsAvailability).coerceAtLeast(0L)
            val bytesToDelete = bytesToDeleteCacheLimit.coerceAtLeast(bytesToDeleteFsLimit)

            Timber.i(
                "File system       : %s of %s available",
                formatBytes(bytesAvailableFs),
                formatBytes(bytesTotalFs)
            )
            Timber.i("Cache limit       : %s", formatBytes(cacheSizeBytes))
            Timber.i("Cache size before : %s", formatBytes(bytesUsedBySubsonic))
            Timber.i("Minimum to delete : %s", formatBytes(bytesToDelete))

            return bytesToDelete
        }

        private fun isPartial(file: AbstractFile): Boolean {
            return file.name.endsWith(".partial") || file.name.contains(".partial.")
        }

        private fun isComplete(file: AbstractFile): Boolean {
            return file.name.endsWith(".complete") || file.name.contains(".complete.")
        }

        @Suppress("NestedBlockDepth")
        private fun deleteFiles(
            files: Collection<AbstractFile>,
            doNotDelete: Collection<String>,
            bytesToDelete: Long,
            deletePartials: Boolean
        ) {
            if (files.isEmpty()) {
                return
            }
            var bytesDeleted = 0L

            for (file in files) {
                if (!deletePartials && bytesDeleted > bytesToDelete) break
                if (bytesToDelete > bytesDeleted || deletePartials && isPartial(file)) {
                    if (!doNotDelete.contains(file.path) && file.name != Constants.ALBUM_ART_FILE) {
                        val size = file.length
                        if (delete(file.path)) {
                            bytesDeleted += size
                        }
                    }
                }
            }
            Timber.i("Deleted: %s", formatBytes(bytesDeleted))
        }

        private fun findCandidatesForDeletion(
            file: AbstractFile,
            files: MutableList<AbstractFile>,
            dirs: MutableList<AbstractFile>
        ) {
            if (file.isFile && (isPartial(file) || isComplete(file))) {
                files.add(file)
            } else {
                // Depth-first
                for (child in listFiles(file)) {
                    findCandidatesForDeletion(child, files, dirs)
                }
                dirs.add(file)
            }
        }

        private fun sortByAscendingModificationTime(files: MutableList<AbstractFile>) {
            files.sortWith { a: AbstractFile, b: AbstractFile ->
                a.lastModified.compareTo(b.lastModified)
            }
        }

        private fun findFilesToNotDelete(): Set<String> {
            val filesToNotDelete: MutableSet<String> = HashSet(5)
            val downloader = inject<Downloader>(
                Downloader::class.java
            )

            for (downloadFile in downloader.value.all) {
                filesToNotDelete.add(downloadFile.partialFile)
                filesToNotDelete.add(downloadFile.completeOrSaveFile)
            }

            filesToNotDelete.add(musicDirectory.path)
            return filesToNotDelete
        }
    }
}
