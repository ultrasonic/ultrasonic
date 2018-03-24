package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class Bookmark(
    val position: Long = 0,
    val username: String = "",
    val comment: String = "",
    val created: Calendar? = null,
    val changed: Calendar? = null,
    val entry: MusicDirectoryChild = MusicDirectoryChild()
)
