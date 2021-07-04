/*
 * FileUtil.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import org.moire.ultrasonic.domain.MusicDirectory
import timber.log.Timber

// TODO: Convert FileUtil.java and merge into here.
object FileUtilKt {
    fun savePlaylist(
        playlistFile: File?,
        playlist: MusicDirectory,
        name: String
    ) {
        val fw = FileWriter(playlistFile)
        val bw = BufferedWriter(fw)

        try {
            fw.write("#EXTM3U\n")
            for (e in playlist.getChildren()) {
                var filePath = FileUtil.getSongFile(e).absolutePath

                if (!File(filePath).exists()) {
                    val ext = FileUtil.getExtension(filePath)
                    val base = FileUtil.getBaseName(filePath)
                    filePath = "$base.complete.$ext"
                }
                fw.write(filePath + "\n")
            }
        } catch (e: IOException) {
            Timber.w("Failed to save playlist: %s", name)
            throw e
        } finally {
            bw.close()
            fw.close()
        }
    }
}
