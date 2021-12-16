/*
 * Storage.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

/**
 * Provides filesystem access abstraction which works
 * both on File based paths and Storage Access Framework Uris
 */
object Storage {

    val mediaRoot: ResettableLazy<AbstractFile> = ResettableLazy {
        getRoot()!!
    }

    fun reset() {
        StorageFile.storageFilePathDictionary.clear()
        StorageFile.notExistingPathDictionary.clear()
        mediaRoot.reset()
        Timber.i("StorageFile caches were reset")
        val root = getRoot()
        if (root == null) {
            Settings.cacheLocation = FileUtil.defaultMusicDirectory.path
            Util.toast(UApp.applicationContext(), R.string.settings_cache_location_error)
        }
    }

    fun getOrCreateFileFromPath(path: String): AbstractFile {
        return mediaRoot.value.getOrCreateFileFromPath(path)
    }

    fun isPathExists(path: String): Boolean {
        return mediaRoot.value.isPathExists(path)
    }

    fun getFromPath(path: String): AbstractFile? {
        return mediaRoot.value.getFromPath(path)
    }

    fun createDirsOnPath(path: String) {
        mediaRoot.value.createDirsOnPath(path)
    }

    fun rename(pathFrom: String, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    fun rename(pathFrom: AbstractFile, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    private fun getRoot(): AbstractFile? {
        return if (Settings.cacheLocation.isUri()) {
            val documentFile = DocumentFile.fromTreeUri(
                UApp.applicationContext(),
                Uri.parse(Settings.cacheLocation)
            ) ?: return null
            if (!documentFile.exists()) return null
            StorageFile(null, documentFile.uri, documentFile.name!!, documentFile.isDirectory)
        } else {
            val file = File(Settings.cacheLocation)
            if (!file.exists()) return null
            JavaFile(null, file)
        }
    }
}

fun String.isUri(): Boolean {
    // TODO is there a better way to tell apart a path and an URI?
    return this.contains(':')
}
