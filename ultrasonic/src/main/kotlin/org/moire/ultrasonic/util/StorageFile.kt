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
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

/**
 * Provides filesystem access abstraction which works
 * both on File based paths and Storage Access Framework Uris
 */
class StorageFile private constructor(
    private var parent: StorageFile?,
    private var abstractFile: AbstractFile,
    private var fileManager: FileManager
): Comparable<StorageFile> {

    override fun compareTo(other: StorageFile): Int {
        return getPath().compareTo(other.getPath())
    }

    var name: String = fileManager.getName(abstractFile)

    var isDirectory: Boolean = fileManager.isDirectory(abstractFile)

    var isFile: Boolean = fileManager.isFile(abstractFile)

    fun length(): Long = fileManager.getLength(abstractFile)

    fun lastModified(): Long = fileManager.lastModified(abstractFile)

    fun delete(): Boolean = fileManager.delete(abstractFile)

    fun listFiles(): Array<StorageFile> {
        val fileList = fileManager.listFiles(abstractFile)
        return fileList.map { file ->  StorageFile(this, file, fileManager) }.toTypedArray()
    }

    fun getFileOutputStream(): OutputStream {
        if (isRawFile()) return File(abstractFile.getFullPath()).outputStream()
        return fileManager.getOutputStream(abstractFile)
            ?: throw IOException("Couldn't retrieve OutputStream")
    }

    fun getFileOutputStream(append: Boolean): OutputStream {
        if (isRawFile()) return FileOutputStream(File(abstractFile.getFullPath()), append)
        val mode = if (append) "wa" else "w"
        val descriptor = UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            abstractFile.getFileRoot<CachingDocumentFile>().holder.uri(), mode)
        return descriptor?.createOutputStream()
            ?: throw IOException("Couldn't retrieve OutputStream")
    }

    fun getFileInputStream(): InputStream {
        if (isRawFile()) return FileInputStream(abstractFile.getFullPath())
        return fileManager.getInputStream(abstractFile)
            ?: throw IOException("Couldn't retrieve InputStream")
    }

    // TODO there are a few functions which could be getters
    // They are functions for now to help us distinguish them from similar getters in File. These can be changed after the refactor is complete.
    fun getPath(): String {
        if (isRawFile()) return abstractFile.getFullPath()
        if (getParent() != null) return getParent()!!.getPath() + "/" + name
        return Uri.parse(abstractFile.getFullPath()).toString()
    }

    fun getParent(): StorageFile? {
        if (isRawFile()) {
            return StorageFile(
                null,
                fileManager.fromRawFile(File(abstractFile.getFullPath()).parentFile!!),
                fileManager
            )
        }
        return parent
    }

    fun isRawFile(): Boolean {
     return abstractFile is RawFile
    }

    fun getRawFilePath(): String? {
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
        // TODO it would be nice to check the access rights and reset the cache directory on error
        private val MusicCacheFileManager: Lazy<FileManager> = lazy {
            val manager = FileManager(UApp.applicationContext())
            manager.registerBaseDir<MusicCacheBaseDirectory>(MusicCacheBaseDirectory())
            manager
        }

        fun getFromParentAndName(parent: StorageFile, name: String): StorageFile {
            val file = parent.fileManager.findFile(parent.abstractFile, name)
                ?: parent.fileManager.createFile(parent.abstractFile, name)!!
            return StorageFile(parent, file, parent.fileManager)
        }

        fun getMediaRoot(): StorageFile {
            return StorageFile(
                null,
                MusicCacheFileManager.value.newBaseDirectoryFile<MusicCacheBaseDirectory>()!!,
                MusicCacheFileManager.value
            )
        }

        // TODO sometimes getFromPath is called after isPathExists, but the file may be gone because it was deleted in another thread.
        // Create a function where these two are merged
        fun getFromPath(path: String): StorageFile {
            Timber.v("StorageFile getFromPath %s", path)
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                return StorageFile(
                    null,
                    MusicCacheFileManager.value.fromPath(normalizedPath),
                    MusicCacheFileManager.value
                )
            }

            val segments = getUriSegments(normalizedPath)
                ?: throw IOException("Can't get path because the root has changed")

            var file = StorageFile(null, getMediaRoot().abstractFile, MusicCacheFileManager.value)
            segments.forEach { segment ->
                file = StorageFile(
                    file,
                    MusicCacheFileManager.value.findFile(file.abstractFile, segment)
                        ?: throw IOException("File not found"),
                    file.fileManager
                )
            }
            return file
        }

        fun getOrCreateFileFromPath(path: String): StorageFile {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                File(normalizedPath).createNewFile()
                return StorageFile(
                    null,
                    MusicCacheFileManager.value.fromPath(normalizedPath),
                    MusicCacheFileManager.value
                )
            }

            val segments = getUriSegments(normalizedPath)
                ?: throw IOException("Can't get path because the root has changed")

            var file = StorageFile(null, getMediaRoot().abstractFile, MusicCacheFileManager.value)
            segments.forEach { segment ->
                file = StorageFile(
                    file,
                    MusicCacheFileManager.value.findFile(file.abstractFile, segment)
                    ?: MusicCacheFileManager.value.createFile(file.abstractFile, segment)!!,
                    file.fileManager
                )
            }
            return file
        }

        fun isPathExists(path: String): Boolean {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) return File(normalizedPath).exists()

            val segments = getUriSegments(normalizedPath) ?: return false

            var file = getMediaRoot().abstractFile
            segments.forEach { segment ->
                file = MusicCacheFileManager.value.findFile(file, segment) ?: return false
            }
            return true
        }

        fun createDirsOnPath(path: String) {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.isUri()) {
                File(normalizedPath).mkdirs()
                return
            }

            val segments = getUriSegments(normalizedPath)
                ?: throw IOException("Can't get path because the root has changed")

            var file = getMediaRoot().abstractFile
            segments.forEach { segment ->
                file = MusicCacheFileManager.value.createDir(file, segment)
                    ?: throw IOException("Can't create directory")
            }
        }

        fun rename(pathFrom: String, pathTo: String) {
            val normalizedPathFrom = normalizePath(pathFrom)
            val normalizedPathTo = normalizePath(pathTo)

            Timber.d("Renaming from %s to %s", normalizedPathFrom, normalizedPathTo)

            val fileFrom = getFromPath(normalizedPathFrom)
            val parentTo = getFromPath(FileUtil.getParentPath(normalizedPathTo)!!)
            val fileTo = getFromParentAndName(parentTo, FileUtil.getNameFromPath(normalizedPathTo))

            MusicCacheFileManager.value.copyFileContents(fileFrom.abstractFile, fileTo.abstractFile)
            fileFrom.delete()
        }

        private fun getUriSegments(uri: String): List<String>? {
            val rootPath = getMediaRoot().getPath()
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
