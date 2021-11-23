/*
 * StorageFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.res.AssetFileDescriptor
import android.net.Uri
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.document_file.CachingDocumentFile
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import org.moire.ultrasonic.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.moire.ultrasonic.app.UApp
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides filesystem access abstraction which works
 * both on File based paths and Storage Access Framework Uris
 */
class StorageFile private constructor(
    private var parentStorageFile: StorageFile?,
    private var abstractFile: AbstractFile,
    private var fileManager: FileManager
): Comparable<StorageFile> {

    override fun compareTo(other: StorageFile): Int {
        return path.compareTo(other.path)
    }

    override fun toString(): String {
        return name
    }

    var name: String = fileManager.getName(abstractFile)

    var isDirectory: Boolean = fileManager.isDirectory(abstractFile)

    var isFile: Boolean = fileManager.isFile(abstractFile)

    val length: Long
        get() = fileManager.getLength(abstractFile)

    val lastModified: Long
        get() = fileManager.lastModified(abstractFile)

    fun delete(): Boolean {
        val deleted = fileManager.delete(abstractFile)
        if (!deleted) return false
        val path = normalizePath(path)
        storageFilePathDictionary.remove(path)
        notExistingPathDictionary.putIfAbsent(path, path)
        return true
    }

    fun listFiles(): Array<StorageFile> {
        val fileList = fileManager.listFiles(abstractFile)
        return fileList.map { file ->  StorageFile(this, file, fileManager) }.toTypedArray()
    }

    fun getFileOutputStream(append: Boolean): OutputStream {
        if (isRawFile) return FileOutputStream(File(abstractFile.getFullPath()), append)
        val mode = if (append) "wa" else "w"
        val descriptor = UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            abstractFile.getFileRoot<CachingDocumentFile>().holder.uri(), mode)
        return descriptor?.createOutputStream()
            ?: throw IOException("Couldn't retrieve OutputStream")
    }

    fun getFileInputStream(): InputStream {
        if (isRawFile) return FileInputStream(abstractFile.getFullPath())
        return fileManager.getInputStream(abstractFile)
            ?: throw IOException("Couldn't retrieve InputStream")
    }

    val path: String
        get() {
            if (isRawFile) return abstractFile.getFullPath()

            // We can't assume that the file's Uri is related to its path,
            // so we generate our own path by concatenating the names on the path.
            if (parentStorageFile != null) return parentStorageFile!!.path + "/" + name
            return Uri.parse(abstractFile.getFullPath()).toString()
        }

    val parent: StorageFile?
        get() {
            if (isRawFile) {
                return StorageFile(
                    null,
                    fileManager.fromRawFile(File(abstractFile.getFullPath()).parentFile!!),
                    fileManager
                )
            }
            return parentStorageFile
        }

    val isRawFile: Boolean
        get() {
            return abstractFile is RawFile
        }

    val rawFilePath: String?
        get() {
            return if (abstractFile is RawFile) abstractFile.getFullPath()
            else null
        }

    fun getDocumentFileDescriptor(openMode: String): AssetFileDescriptor? {
        return if (abstractFile !is RawFile) {
            UApp.applicationContext().contentResolver.openAssetFileDescriptor(
                abstractFile.getFileRoot<CachingDocumentFile>().holder.uri(),
                openMode
            )
        } else null
    }

    companion object {
        // These caches are necessary because SAF is very slow, and the caching in FSAF is buggy.
        // Ultrasonic assumes that the files won't change while it is in the foreground.
        // TODO to really handle concurrency we'd need API24.
        // If this isn't good enough we can add locking.
        private val storageFilePathDictionary = ConcurrentHashMap<String, StorageFile>()
        private val notExistingPathDictionary = ConcurrentHashMap<String, String>()

        private val fileManager: ResettableLazy<FileManager> = ResettableLazy {
            val manager = FileManager(UApp.applicationContext())
            manager.registerBaseDir<MusicCacheBaseDirectory>(MusicCacheBaseDirectory())
            manager
        }

        val mediaRoot: ResettableLazy<StorageFile> = ResettableLazy {
            StorageFile(
                null,
                fileManager.value.newBaseDirectoryFile<MusicCacheBaseDirectory>()!!,
                fileManager.value
            )
        }

        fun resetCaches() {
            storageFilePathDictionary.clear()
            notExistingPathDictionary.clear()
            fileManager.value.unregisterBaseDir<MusicCacheBaseDirectory>()
            fileManager.reset()
            mediaRoot.reset()
            Timber.v("StorageFile caches were reset")
            if (!fileManager.value.baseDirectoryExists<MusicCacheBaseDirectory>()) {
                Settings.cacheLocation = FileUtil.defaultMusicDirectory.path
                Util.toast(UApp.applicationContext(), R.string.settings_cache_location_error)
            }
        }

        fun getOrCreateFileFromPath(path: String): StorageFile {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                File(normalizedPath).createNewFile()
                return StorageFile(
                    null,
                    fileManager.value.fromPath(normalizedPath),
                    fileManager.value
                )
            }

            if (storageFilePathDictionary.containsKey(normalizedPath))
                return storageFilePathDictionary[normalizedPath]!!

            val parent = getStorageFileForParentDirectory(normalizedPath)
                ?: throw IOException("Parent directory doesn't exist")

            val name = FileUtil.getNameFromPath(normalizedPath)
            val file = StorageFile(
                parent,
                fileManager.value.findFile(parent.abstractFile, name)
                    ?: fileManager.value.create(parent.abstractFile,
                        listOf(FileSegment(name))
                    )!!,
                parent.fileManager
            )
            storageFilePathDictionary[normalizedPath] = file
            notExistingPathDictionary.remove(normalizedPath)
            return file
        }

        fun isPathExists(path: String): Boolean {
            return getFromPath(path) != null
        }

        fun getFromPath(path: String): StorageFile? {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                val file = fileManager.value.fromPath(normalizedPath)
                if (!fileManager.value.exists(file)) return null
                return StorageFile(null, file, fileManager.value)
            }

            if (storageFilePathDictionary.containsKey(normalizedPath))
                return storageFilePathDictionary[normalizedPath]!!
            if (notExistingPathDictionary.contains(normalizedPath)) return null

            val parent = getStorageFileForParentDirectory(normalizedPath)
            if (parent == null) {
                notExistingPathDictionary.putIfAbsent(normalizedPath, normalizedPath)
                return null
            }

            val fileName = FileUtil.getNameFromPath(normalizedPath)
            var file: StorageFile? = null

            // Listing a bunch of files takes the same time in SAF as finding one,
            // so we list and cache all of them for performance
            parent.listFiles().forEach {
                if (it.name == fileName) file = it
                storageFilePathDictionary[it.path] = it
                notExistingPathDictionary.remove(it.path)
            }

            if (file == null) {
                notExistingPathDictionary.putIfAbsent(normalizedPath, normalizedPath)
                return null
            }

            return file
        }

        fun createDirsOnPath(path: String) {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                File(normalizedPath).mkdirs()
                return
            }

            val segments = getUriSegments(normalizedPath)
                ?: throw IOException("Can't get path because the root has changed")

            var file = mediaRoot.value
            segments.forEach { segment ->
                file = StorageFile(
                    file,
                    fileManager.value.create(file.abstractFile, listOf(DirectorySegment(segment)))
                        ?: throw IOException("Can't create directory"),
                    fileManager.value
                )

                notExistingPathDictionary.remove(normalizePath(file.path))
            }
        }

        fun rename(pathFrom: String, pathTo: String) {
            val normalizedPathFrom = normalizePath(pathFrom)
            val fileFrom = getFromPath(normalizedPathFrom) ?: throw IOException("File to rename doesn't exist")
            rename(fileFrom, pathTo)
        }

        fun rename(pathFrom: StorageFile?, pathTo: String) {
            val normalizedPathTo = normalizePath(pathTo)
            if (pathFrom == null || !pathFrom.fileManager.exists(pathFrom.abstractFile)) throw IOException("File to rename doesn't exist")
            Timber.d("Renaming from %s to %s", pathFrom.path, normalizedPathTo)

            val parentTo = getFromPath(FileUtil.getParentPath(normalizedPathTo)!!) ?: throw IOException("Destination folder doesn't exist")
            val fileTo = getFromParentAndName(parentTo, FileUtil.getNameFromPath(normalizedPathTo))
            notExistingPathDictionary.remove(normalizedPathTo)
            storageFilePathDictionary.remove(normalizePath(pathFrom.path))

            fileManager.value.copyFileContents(pathFrom.abstractFile, fileTo.abstractFile)
            pathFrom.delete()
        }

        private fun getFromParentAndName(parent: StorageFile, name: String): StorageFile {
            val file = parent.fileManager.findFile(parent.abstractFile, name)
                ?: parent.fileManager.createFile(parent.abstractFile, name)!!
            return StorageFile(parent, file, parent.fileManager)
        }

        private fun getStorageFileForParentDirectory(path: String): StorageFile? {
            val parentPath = FileUtil.getParentPath(path)!!
            if (storageFilePathDictionary.containsKey(parentPath))
                return storageFilePathDictionary[parentPath]!!
            if (notExistingPathDictionary.contains(parentPath)) return null

            val parent = findStorageFileForParentDirectory(parentPath)
            if (parent == null) {
                storageFilePathDictionary.remove(parentPath)
                notExistingPathDictionary.putIfAbsent(parentPath, parentPath)
            } else {
                storageFilePathDictionary[parentPath] = parent
                notExistingPathDictionary.remove(parentPath)
            }

            return parent
        }

        private fun findStorageFileForParentDirectory(path: String): StorageFile? {
            val segments = getUriSegments(path)
                ?: throw IOException("Can't get path because the root has changed")

            var file = StorageFile(null, mediaRoot.value.abstractFile, fileManager.value)
            segments.forEach { segment ->
                file = StorageFile(
                    file,
                    fileManager.value.findFile(file.abstractFile, segment)
                        ?: return null,
                    file.fileManager
                )
            }
            return file
        }

        private fun getUriSegments(uri: String): List<String>? {
            val rootPath = mediaRoot.value.path
            if (!uri.startsWith(rootPath)) return null
            val pathWithoutRoot = uri.substringAfter(rootPath)
            return pathWithoutRoot.split('/').filter { it.isNotEmpty() }
        }

        private fun normalizePath(path: String): String {
            // FSAF replaces spaces in paths with "_", so we must do the same everywhere
            // TODO paths sometimes contain double "/". These are currently replaced to single one.
            // The nice solution would be to check and fix why this happens
            return path.replace(' ', '_').replace(Regex("(?<!:)//"), "/")
        }
    }
}

class MusicCacheBaseDirectory : BaseDirectory() {

    override fun getDirFile(): File {
        return FileUtil.defaultMusicDirectory
    }

    override fun getDirUri(): Uri? {
        if (!Settings.cacheLocation.isUri()) return null
        return Uri.parse(Settings.cacheLocation)
    }

    override fun currentActiveBaseDirType(): ActiveBaseDirType {
        return when {
            Settings.cacheLocation.isUri() -> ActiveBaseDirType.SafBaseDir
            else -> ActiveBaseDirType.JavaFileBaseDir
        }
    }
}

fun String.isUri(): Boolean {
    // TODO is there a better way to tell apart a path and an URI?
    return this.contains(':')
}
