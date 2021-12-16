/*
 * StorageFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

/**
 * The DocumentsContract based implementation of AbstractFile
 * This class is used when a user selected directory is set as media storage
 */
class StorageFile(
    override val parent: StorageFile?,
    var uri: Uri,
    override val name: String,
    override val isDirectory: Boolean
) : AbstractFile() {
    override val isFile: Boolean = !isDirectory

    override val length: Long
        get() {
            try {
                val resolver = UApp.applicationContext().contentResolver
                val column = arrayOf(DocumentsContract.Document.COLUMN_SIZE)
                resolver.query(uri, column, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(0)
                    }
                }
            } catch (_: IllegalArgumentException) {
                Timber.d("Tried to get length of $uri but it probably doesn't exists")
            }
            return 0
        }

    override val lastModified: Long
        get() {
            try {
                val resolver = UApp.applicationContext().contentResolver
                val column = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                resolver.query(uri, column, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(0)
                    }
                }
            } catch (_: IllegalArgumentException) {
                Timber.d("Tried to get length of $uri but it probably doesn't exists")
            }
            return 0
        }

    override val path: String
        get() {
            // We can't assume that the file's Uri is related to its path,
            // so we generate our own path by concatenating the names on the path.
            if (parent != null) return parent.path + "/" + name
            return uri.toString()
        }

    override fun delete(): Boolean {
        val deleted = DocumentsContract.deleteDocument(
            UApp.applicationContext().contentResolver,
            uri
        )
        if (!deleted) return false
        storageFilePathDictionary.remove(path)
        notExistingPathDictionary.putIfAbsent(path, path)
        return true
    }

    override fun listFiles(): Array<AbstractFile> {
        return getChildren().toTypedArray()
    }

    override fun getFileOutputStream(append: Boolean): OutputStream {
        val mode = if (append) "wa" else "w"
        val descriptor = UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            uri,
            mode
        )
        return descriptor?.createOutputStream()
            ?: throw IOException("Couldn't retrieve OutputStream")
    }

    override fun getFileInputStream(): InputStream {
        return UApp.applicationContext().contentResolver.openInputStream(uri)
            ?: throw IOException("Couldn't retrieve InputStream")
    }

    override fun getDocumentFileDescriptor(openMode: String): AssetFileDescriptor? {
        return UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            uri,
            openMode
        )
    }

    @Synchronized
    override fun getOrCreateFileFromPath(path: String): AbstractFile {
        if (storageFilePathDictionary.containsKey(path))
            return storageFilePathDictionary[path]!!

        val parent = getStorageFileForParentDirectory(path)
            ?: throw IOException("Parent directory doesn't exist")

        val name = FileUtil.getNameFromPath(path)
        val file = getFromParentAndName(parent, name)

        storageFilePathDictionary[path] = file
        notExistingPathDictionary.remove(path)
        return file
    }

    override fun isPathExists(path: String): Boolean {
        return getFromPath(path) != null
    }

    override fun getFromPath(path: String): StorageFile? {
        if (storageFilePathDictionary.containsKey(path))
            return storageFilePathDictionary[path]!!
        if (notExistingPathDictionary.contains(path)) return null

        val parent = getStorageFileForParentDirectory(path)
        if (parent == null) {
            notExistingPathDictionary.putIfAbsent(path, path)
            return null
        }

        val fileName = FileUtil.getNameFromPath(path)
        var file: StorageFile? = null

        parent.listFiles().forEach {
            if (it.name == fileName) file = it as StorageFile
            storageFilePathDictionary[it.path] = it as StorageFile
            notExistingPathDictionary.remove(it.path)
        }

        if (file == null) {
            notExistingPathDictionary.putIfAbsent(path, path)
            return null
        }

        return file
    }

    @Synchronized
    override fun createDirsOnPath(path: String) {
        val segments = getUriSegments(path)
            ?: throw IOException("Can't get path because the root has changed")

        var file = Storage.mediaRoot.value as StorageFile
        segments.forEach { segment ->
            val foundFile = file.listFiles().singleOrNull { it.name == segment }
            if (foundFile != null) {
                file = foundFile as StorageFile
            } else {
                val createdUri = DocumentsContract.createDocument(
                    UApp.applicationContext().contentResolver,
                    file.uri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment
                ) ?: throw IOException("Can't create directory")

                file = StorageFile(file, createdUri, segment, true)
            }
            notExistingPathDictionary.remove(file.path)
        }
    }

    @Synchronized
    override fun rename(pathFrom: AbstractFile, pathTo: String) {
        val fileFrom = pathFrom as StorageFile
        if (!fileFrom.exists()) throw IOException("File to rename doesn't exist")
        Timber.d("Renaming from %s to %s", fileFrom.path, pathTo)

        val parentTo = getFromPath(FileUtil.getParentPath(pathTo)!!)
            ?: throw IOException("Destination folder doesn't exist")
        val fileTo = getFromParentAndName(parentTo, FileUtil.getNameFromPath(pathTo))

        copyFileContents(fileFrom, fileTo)
        fileFrom.delete()

        notExistingPathDictionary.remove(pathTo)
        storageFilePathDictionary.remove(fileFrom.path)
    }

    private fun exists(): Boolean {
        val resolver = UApp.applicationContext().contentResolver
        val column = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(uri, column, null, null, null)?.use { cursor ->
            if (cursor.count != 0) return true
        }
        return false
    }

    private fun getChildren(): List<StorageFile> {
        val resolver = UApp.applicationContext().contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri)
        )

        return resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            val result = mutableListOf<StorageFile>()

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val displayName = cursor.getString(1)
                val mimeType = cursor.getString(2)

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

                val storageFile = StorageFile(
                    this,
                    documentUri,
                    displayName,
                    (mimeType == DocumentsContract.Document.MIME_TYPE_DIR)
                )

                result += storageFile
            }
            return@use result
        } ?: emptyList()
    }

    companion object {
        // These caches are necessary because SAF is very slow.
        // Ultrasonic assumes that the files won't change while it is in the foreground.
        // TODO to really handle concurrency we'd need API24.
        // If this isn't good enough we can add locking.
        val storageFilePathDictionary = ConcurrentHashMap<String, StorageFile>()
        val notExistingPathDictionary = ConcurrentHashMap<String, String>()

        val mimeTypeMap: MimeTypeMap = MimeTypeMap.getSingleton()

        private val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        private fun copyFileContents(sourceFile: AbstractFile, destinationFile: AbstractFile) {
            sourceFile.getFileInputStream().use { inputStream ->
                destinationFile.getFileOutputStream(false).use { outputStream ->
                    inputStream.copyInto(outputStream)
                }
            }
        }

        private fun getFromParentAndName(parent: StorageFile, name: String): StorageFile {
            val foundFile = parent.listFiles().firstOrNull { it.name == name }

            if (foundFile != null) return foundFile as StorageFile

            val createdUri = DocumentsContract.createDocument(
                UApp.applicationContext().contentResolver,
                parent.uri,
                mimeTypeMap.getMimeTypeFromExtension(name.extension())!!,
                name.withoutExtension()
            ) ?: throw IOException("Can't create file")

            return StorageFile(parent, createdUri, name, false)
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

        @Suppress("NestedBlockDepth")
        private fun findStorageFileForParentDirectory(path: String): StorageFile? {
            val segments = getUriSegments(path)
                ?: throw IOException("Can't get path because the root has changed")

            var file = Storage.mediaRoot.value as StorageFile
            segments.forEach { segment ->
                val currentPath = file.path + "/" + segment
                if (notExistingPathDictionary.contains(currentPath)) return null
                if (storageFilePathDictionary.containsKey(currentPath)) {
                    file = storageFilePathDictionary[currentPath]!!
                } else {
                    var foundFile: StorageFile? = null
                    file.listFiles().forEach {
                        if (it.name == segment) foundFile = it as StorageFile
                        storageFilePathDictionary[it.path] = it as StorageFile
                        notExistingPathDictionary.remove(it.path)
                    }

                    if (foundFile == null) {
                        notExistingPathDictionary.putIfAbsent(path, path)
                        return null
                    }

                    file = foundFile!!
                }
            }
            return file
        }

        private fun getUriSegments(uri: String): List<String>? {
            val rootPath = Storage.mediaRoot.value.path
            if (!uri.startsWith(rootPath)) return null
            val pathWithoutRoot = uri.substringAfter(rootPath)
            return pathWithoutRoot.split('/').filter { it.isNotEmpty() }
        }
    }
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
