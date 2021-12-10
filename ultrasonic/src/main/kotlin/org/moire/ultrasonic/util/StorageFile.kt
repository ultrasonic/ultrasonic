/*
 * StorageFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import org.moire.ultrasonic.R
import java.io.File
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
    private var documentFile: DocumentFile
): Comparable<StorageFile> {

    override fun compareTo(other: StorageFile): Int {
        return path.compareTo(other.path)
    }

    override fun toString(): String {
        return name
    }

    var name: String = documentFile.name!!

    var isDirectory: Boolean = documentFile.isDirectory

    var isFile: Boolean = documentFile.isFile

    val length: Long
        get() = documentFile.length()

    val lastModified: Long
        get() = documentFile.lastModified()

    fun delete(): Boolean {
        val deleted = documentFile.delete()
        if (!deleted) return false
        storageFilePathDictionary.remove(path)
        notExistingPathDictionary.putIfAbsent(path, path)
        listedPathDictionary.remove(path)
        listedPathDictionary.remove(parent?.path)
        return true
    }

    fun listFiles(): Array<StorageFile> {
        val fileList = documentFile.listFiles()
        return fileList.map { file ->  StorageFile(this, file) }.toTypedArray()
    }

    fun getFileOutputStream(append: Boolean): OutputStream {
        val mode = if (append) "wa" else "w"
        val descriptor = UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            documentFile.uri, mode)
        return descriptor?.createOutputStream()
            ?: throw IOException("Couldn't retrieve OutputStream")
    }

    fun getFileInputStream(): InputStream {
        return UApp.applicationContext().contentResolver.openInputStream(documentFile.uri)
            ?: throw IOException("Couldn't retrieve InputStream")
    }

    val path: String
        get() {
            // We can't assume that the file's Uri is related to its path,
            // so we generate our own path by concatenating the names on the path.
            if (parentStorageFile != null) return parentStorageFile!!.path + "/" + name
            return documentFile.uri.toString()
        }

    val parent: StorageFile?
        get() {
            return parentStorageFile
        }

    fun getDocumentFileDescriptor(openMode: String): AssetFileDescriptor? {
        return UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            documentFile.uri,
            openMode
        )
    }

    companion object {
        // These caches are necessary because SAF is very slow, and the caching in FSAF is buggy.
        // Ultrasonic assumes that the files won't change while it is in the foreground.
        // TODO to really handle concurrency we'd need API24.
        // If this isn't good enough we can add locking.
        private val storageFilePathDictionary = ConcurrentHashMap<String, StorageFile>()
        private val notExistingPathDictionary = ConcurrentHashMap<String, String>()
        private val listedPathDictionary = ConcurrentHashMap<String, String>()

        val mediaRoot: ResettableLazy<StorageFile> = ResettableLazy {
                StorageFile(null, getRoot()!!)
        }

        private fun getRoot(): DocumentFile? {
            return if (Settings.cacheLocation.isUri()) {
                DocumentFile.fromTreeUri(
                    UApp.applicationContext(),
                    Uri.parse(Settings.cacheLocation)
                )
            } else {
                DocumentFile.fromFile(File(Settings.cacheLocation))
            }
        }

        fun resetCaches() {
            storageFilePathDictionary.clear()
            notExistingPathDictionary.clear()
            listedPathDictionary.clear()
            mediaRoot.reset()
            Timber.i("StorageFile caches were reset")
            val root = getRoot()
            if (root == null || !root.exists()) {
                Settings.cacheLocation = FileUtil.defaultMusicDirectory.path
                Util.toast(UApp.applicationContext(), R.string.settings_cache_location_error)
            }
        }

        @Synchronized
        fun getOrCreateFileFromPath(path: String): StorageFile {
            if (storageFilePathDictionary.containsKey(path))
                return storageFilePathDictionary[path]!!

            val parent = getStorageFileForParentDirectory(path)
                ?: throw IOException("Parent directory doesn't exist")

            val name = FileUtil.getNameFromPath(path)
            val file = StorageFile(
                parent,
                parent.documentFile.findFile(name)
                    ?: parent.documentFile.createFile(
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.extension())!!,
                        name.withoutExtension()
                    )!!
            )

            storageFilePathDictionary[path] = file
            notExistingPathDictionary.remove(path)
            return file
        }

        fun isPathExists(path: String): Boolean {
            return getFromPath(path) != null
        }

        fun getFromPath(path: String): StorageFile? {

            if (storageFilePathDictionary.containsKey(path))
                return storageFilePathDictionary[path]!!
            if (notExistingPathDictionary.contains(path)) return null

            val parent = getStorageFileForParentDirectory(path)
            if (parent == null) {
                notExistingPathDictionary.putIfAbsent(path, path)
                return null
            }

            // If the parent was fully listed, but the searched file isn't cached, it doesn't exists.
            if (listedPathDictionary.containsKey(parent.path)) return null

            val fileName = FileUtil.getNameFromPath(path)
            var file: StorageFile? = null

            //Timber.v("StorageFile getFromPath path: %s", path)
            // Listing a bunch of files takes the same time in SAF as finding one,
            // so we list and cache all of them for performance

            parent.listFiles().forEach {
                if (it.name == fileName) file = it
                storageFilePathDictionary[it.path] = it
                notExistingPathDictionary.remove(it.path)
            }

            listedPathDictionary[parent.path] = parent.path

            if (file == null) {
                notExistingPathDictionary.putIfAbsent(path, path)
                return null
            }

            return file
        }

        @Synchronized
        fun createDirsOnPath(path: String) {
            val segments = getUriSegments(path)
                ?: throw IOException("Can't get path because the root has changed")

            var file = mediaRoot.value
            segments.forEach { segment ->
                file = StorageFile(
                    file,
                    file.documentFile.findFile(segment) ?:
                    file.documentFile.createDirectory(segment)
                        ?: throw IOException("Can't create directory")
                )

                notExistingPathDictionary.remove(file.path)
                listedPathDictionary.remove(file.path)
            }
        }

        fun rename(pathFrom: String, pathTo: String) {
            val fileFrom = getFromPath(pathFrom) ?: throw IOException("File to rename doesn't exist")
            rename(fileFrom, pathTo)
        }

        @Synchronized
        fun rename(pathFrom: StorageFile?, pathTo: String) {
            if (pathFrom == null || !pathFrom.documentFile.exists()) throw IOException("File to rename doesn't exist")
            Timber.d("Renaming from %s to %s", pathFrom.path, pathTo)

            val parentTo = getFromPath(FileUtil.getParentPath(pathTo)!!) ?: throw IOException("Destination folder doesn't exist")
            val fileTo = getFromParentAndName(parentTo, FileUtil.getNameFromPath(pathTo))

            copyFileContents(pathFrom.documentFile, fileTo.documentFile)
            pathFrom.delete()

            notExistingPathDictionary.remove(pathTo)
            storageFilePathDictionary.remove(pathFrom.path)
        }

        private fun copyFileContents(sourceFile: DocumentFile, destinationFile: DocumentFile) {
            UApp.applicationContext().contentResolver.openInputStream(sourceFile.uri)?.use { inputStream ->
                UApp.applicationContext().contentResolver.openOutputStream(destinationFile.uri)?.use { outputStream ->
                    inputStream.copyInto(outputStream)
                }
            }
        }

        private fun getFromParentAndName(parent: StorageFile, name: String): StorageFile {
            val file = parent.documentFile.findFile(name)
                ?: parent.documentFile.createFile(
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.extension())!!,
                    name.withoutExtension()
                )!!
            return StorageFile(parent, file)
        }

        private fun getStorageFileForParentDirectory(path: String): StorageFile? {
            val parentPath = FileUtil.getParentPath(path)!!
            if (storageFilePathDictionary.containsKey(parentPath))
                return storageFilePathDictionary[parentPath]!!
            if (notExistingPathDictionary.contains(parentPath)) return null

            //val start = System.currentTimeMillis()
            val parent = findStorageFileForParentDirectory(parentPath)
            //val end = System.currentTimeMillis()
            //Timber.v("StorageFile getStorageFileForParentDirectory searching for %s, time: %d", parentPath, end-start)

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

            var file = StorageFile(null, mediaRoot.value.documentFile)
            segments.forEach { segment ->
                val currentPath = file.path + "/" + segment
                if (notExistingPathDictionary.contains(currentPath)) return null
                if (storageFilePathDictionary.containsKey(currentPath)) {
                    file = storageFilePathDictionary[currentPath]!!
                } else {
                    // If the parent was fully listed, but the searched file isn't cached, it doesn't exists.
                    if (listedPathDictionary.containsKey(file.path)) return null
                    var foundFile: StorageFile? = null
                    file.listFiles().forEach {
                        if (it.name == segment) foundFile = it
                        storageFilePathDictionary[it.path] = it
                        notExistingPathDictionary.remove(it.path)
                    }

                    listedPathDictionary[file.path] = file.path

                    if (foundFile == null) {
                        notExistingPathDictionary.putIfAbsent(path, path)
                        return null
                    }

                    file = StorageFile(file, foundFile!!.documentFile)
                }
            }
            return file
        }

        private fun getUriSegments(uri: String): List<String>? {
            val rootPath = mediaRoot.value.path
            if (!uri.startsWith(rootPath)) return null
            val pathWithoutRoot = uri.substringAfter(rootPath)
            return pathWithoutRoot.split('/').filter { it.isNotEmpty() }
        }
    }
}

fun String.isUri(): Boolean {
    // TODO is there a better way to tell apart a path and an URI?
    return this.contains(':')
}

fun String.extension(): String {
    val index = this.indexOfLast { ch -> ch == '.' }
    if (index == -1) return ""
    if (index == this.lastIndex) return ""
    return this.substring(index + 1)
}

fun String.withoutExtension(): String {
    val index = this.indexOfLast { ch -> ch == '.' }
    if (index == -1) return this
    return this.substring(0, index)
}

fun InputStream.copyInto(outputStream: OutputStream) {
    var read: Int
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        read = this.read(buffer)
        if (read == -1) {
            break
        }
        outputStream.write(buffer, 0, read)
    }
}
