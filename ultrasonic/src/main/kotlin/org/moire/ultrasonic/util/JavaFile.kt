/*
 * JavaFile.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.res.AssetFileDescriptor
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.moire.ultrasonic.app.UApp

class JavaFile(override val parent: AbstractFile?, val file: File) : AbstractFile() {
    override val name: String = file.name
    override val isDirectory: Boolean = file.isDirectory
    override val isFile: Boolean = file.isFile
    override val length: Long
        get() = file.length()
    override val lastModified: Long
        get() = file.lastModified()
    override val path: String
        get() = file.absolutePath

    override fun delete(): Boolean {
        return file.delete()
    }

    override fun listFiles(): Array<AbstractFile> {
        val fileList = file.listFiles()
        return fileList?.map { file -> JavaFile(this, file) }?.toTypedArray() ?: emptyArray()
    }

    override fun getFileOutputStream(append: Boolean): OutputStream {
        return FileOutputStream(file, append)
    }

    override fun getFileInputStream(): InputStream {
        return FileInputStream(file)
    }

    override fun getDocumentFileDescriptor(openMode: String): AssetFileDescriptor? {
        val documentFile = DocumentFile.fromFile(file)
        return UApp.applicationContext().contentResolver.openAssetFileDescriptor(
            documentFile.uri,
            openMode
        )
    }

    override fun getOrCreateFileFromPath(path: String): AbstractFile {
        File(path).createNewFile()
        return JavaFile(null, File(path))
    }

    override fun isPathExists(path: String): Boolean {
        return File(path).exists()
    }

    override fun getFromPath(path: String): AbstractFile {
        return JavaFile(null, File(path))
    }

    override fun createDirsOnPath(path: String) {
        File(path).mkdirs()
    }

    override fun rename(pathFrom: AbstractFile, pathTo: String) {
        val javaFile = pathFrom as JavaFile
        javaFile.file.copyTo(File(pathTo))
        javaFile.file.delete()
    }
}
