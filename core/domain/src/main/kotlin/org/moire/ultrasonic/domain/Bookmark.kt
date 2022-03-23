package org.moire.ultrasonic.domain

import java.io.Serializable
import java.util.Date
import org.moire.ultrasonic.domain.MusicDirectory.Track

data class Bookmark(
    val position: Int = 0,
    val username: String,
    val comment: String,
    val created: Date? = null,
    val changed: Date? = null,
    val track: Track
) : Serializable {
    companion object {
        private const val serialVersionUID = 8988990025189807803L
    }
}
