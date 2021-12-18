/*
 * AbstractFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.res.AssetFileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Contains the abstract file operations which Ultrasonic uses during the media store access
 */
abstract class AbstractFile : Comparable<AbstractFile> {
    abstract val name: String
    abstract val isDirectory: Boolean
    abstract val isFile: Boolean
    abstract val length: Long
    abstract val lastModified: Long
    abstract val path: String
    abstract val parent: AbstractFile?

    override fun compareTo(other: AbstractFile): Int {
        return path.compareTo(other.path)
    }

    override fun toString(): String {
        return name
    }

    abstract fun delete(): Boolean

    abstract fun listFiles(): Array<AbstractFile>

    abstract fun getFileOutputStream(append: Boolean): OutputStream

    abstract fun getFileInputStream(): InputStream

    abstract fun getDocumentFileDescriptor(openMode: String): AssetFileDescriptor?

    abstract fun getOrCreateFileFromPath(path: String): AbstractFile

    abstract fun isPathExists(path: String): Boolean

    abstract fun getFromPath(path: String): AbstractFile?

    abstract fun createDirsOnPath(path: String)

    fun rename(pathFrom: String, pathTo: String) {
        val fileFrom = getFromPath(pathFrom) ?: throw IOException("File to rename doesn't exist")
        rename(fileFrom, pathTo)
    }

    abstract fun rename(pathFrom: AbstractFile, pathTo: String)
}
