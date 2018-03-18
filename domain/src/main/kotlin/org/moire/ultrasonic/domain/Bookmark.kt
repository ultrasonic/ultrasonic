package org.moire.ultrasonic.domain

import org.moire.ultrasonic.domain.MusicDirectory.Entry

import java.io.Serializable
import java.util.Date

data class Bookmark(
        val position: Int = 0,
        val username: String,
        val comment: String,
        val created: Date? = null,
        val changed: Date? = null,
        val entry: Entry
) : Serializable {
    companion object {
        private const val serialVersionUID = 8988990025189807803L
    }
}
