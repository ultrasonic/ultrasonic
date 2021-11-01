package org.moire.ultrasonic.util

import android.os.AsyncTask
import android.os.StatFs
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.service.Downloader
import org.moire.ultrasonic.util.FileUtil.getAlbumArtFile
import org.moire.ultrasonic.util.FileUtil.getPlaylistDirectory
import org.moire.ultrasonic.util.FileUtil.getPlaylistFile
import org.moire.ultrasonic.util.FileUtil.listFiles
import org.moire.ultrasonic.util.FileUtil.musicDirectory
import org.moire.ultrasonic.util.Settings.cacheSizeMB
import org.moire.ultrasonic.util.Util.delete
import org.moire.ultrasonic.util.Util.formatBytes
import timber.log.Timber

/**
 * Responsible for cleaning up files from the offline download cache on the filesystem.
 */
class CacheCleaner {
    fun clean() {
        try {
            BackgroundCleanup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } catch (all: Exception) {
            // If an exception is thrown, assume we execute correctly the next time
            Timber.w(all, "Exception in CacheCleaner.clean")
        }
    }

    fun cleanSpace() {
        try {
            BackgroundSpaceCleanup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } catch (all: Exception) {
            // If an exception is thrown, assume we execute correctly the next time
            Timber.w(all, "Exception in CacheCleaner.cleanSpace")
        }
    }

    fun cleanPlaylists(playlists: List<Playlist>) {
        try {
            BackgroundPlaylistsCleanup().executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                playlists
            )
        } catch (all: Exception) {
            // If an exception is thrown, assume we execute correctly the next time
            Timber.w(all, "Exception in CacheCleaner.cleanPlaylists")
        }
    }

    private class BackgroundCleanup : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            try {
                Thread.currentThread().name = "BackgroundCleanup"
                val files: MutableList<File> = ArrayList()
                val dirs: MutableList<File> = ArrayList()
                findCandidatesForDeletion(musicDirectory, files, dirs)
                sortByAscendingModificationTime(files)
                val filesToNotDelete = findFilesToNotDelete()
                deleteFiles(files, filesToNotDelete, getMinimumDelete(files), true)
                deleteEmptyDirs(dirs, filesToNotDelete)
            } catch (all: RuntimeException) {
                Timber.e(all, "Error in cache cleaning.")
            }
            return null
        }
    }

    private class BackgroundSpaceCleanup : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            try {
                Thread.currentThread().name = "BackgroundSpaceCleanup"
                val files: MutableList<File> = ArrayList()
                val dirs: MutableList<File> = ArrayList()
                findCandidatesForDeletion(musicDirectory, files, dirs)
                val bytesToDelete = getMinimumDelete(files)
                if (bytesToDelete > 0L) {
                    sortByAscendingModificationTime(files)
                    val filesToNotDelete = findFilesToNotDelete()
                    deleteFiles(files, filesToNotDelete, bytesToDelete, false)
                }
            } catch (all: RuntimeException) {
                Timber.e(all, "Error in cache cleaning.")
            }
            return null
        }
    }

    private class BackgroundPlaylistsCleanup : AsyncTask<List<Playlist>, Void?, Void?>() {
        override fun doInBackground(vararg params: List<Playlist>): Void? {
            try {
                val activeServerProvider = inject<ActiveServerProvider>(
                    ActiveServerProvider::class.java
                )
                Thread.currentThread().name = "BackgroundPlaylistsCleanup"
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
            }
            return null
        }
    }

    companion object {
        private const val MIN_FREE_SPACE = 500 * 1024L * 1024L
        private fun deleteEmptyDirs(dirs: Iterable<File>, doNotDelete: Collection<File>) {
            for (dir in dirs) {
                if (doNotDelete.contains(dir)) {
                    continue
                }
                var children = dir.listFiles()
                if (children != null) {
                    // No songs left in the folder
                    if (children.size == 1 && children[0].path == getAlbumArtFile(dir).path) {
                        // Delete Artwork files
                        delete(getAlbumArtFile(dir))
                        children = dir.listFiles()
                    }

                    // Delete empty directory
                    if (children != null && children.isEmpty()) {
                        delete(dir)
                    }
                }
            }
        }

        private fun getMinimumDelete(files: List<File>): Long {
            if (files.isEmpty()) {
                return 0L
            }
            val cacheSizeBytes = cacheSizeMB * 1024L * 1024L
            var bytesUsedBySubsonic = 0L
            for (file in files) {
                bytesUsedBySubsonic += file.length()
            }

            // Ensure that file system is not more than 95% full.
            val stat = StatFs(files[0].path)
            val bytesTotalFs = stat.blockCountLong * stat.blockSizeLong
            val bytesAvailableFs = stat.availableBlocksLong * stat.blockSizeLong
            val bytesUsedFs = bytesTotalFs - bytesAvailableFs
            val minFsAvailability = bytesTotalFs - MIN_FREE_SPACE
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

        private fun isPartial(file: File): Boolean {
            return file.name.endsWith(".partial") || file.name.contains(".partial.")
        }

        private fun isComplete(file: File): Boolean {
            return file.name.endsWith(".complete") || file.name.contains(".complete.")
        }

        @Suppress("NestedBlockDepth")
        private fun deleteFiles(
            files: Collection<File>,
            doNotDelete: Collection<File>,
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
                    if (!doNotDelete.contains(file) && file.name != Constants.ALBUM_ART_FILE) {
                        val size = file.length()
                        if (delete(file)) {
                            bytesDeleted += size
                        }
                    }
                }
            }
            Timber.i("Deleted: %s", formatBytes(bytesDeleted))
        }

        private fun findCandidatesForDeletion(
            file: File,
            files: MutableList<File>,
            dirs: MutableList<File>
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

        private fun sortByAscendingModificationTime(files: MutableList<File>) {
            files.sortWith { a: File, b: File ->
                a.lastModified().compareTo(b.lastModified())
            }
        }

        private fun findFilesToNotDelete(): Set<File> {
            val filesToNotDelete: MutableSet<File> = HashSet(5)
            val downloader = inject<Downloader>(
                Downloader::class.java
            )
            for (downloadFile in downloader.value.all) {
                filesToNotDelete.add(downloadFile.partialFile)
                filesToNotDelete.add(downloadFile.completeOrSaveFile)
            }
            filesToNotDelete.add(musicDirectory)
            return filesToNotDelete
        }
    }
}
