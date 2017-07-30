package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class MusicDirectoryChild(val id: Long, val parent: Long, val isDir: Boolean = false,
                               val title: String = "", val album: String = "",
                               val artist: String = "", val year: Int?,
                               val genre: String = "", val coverArt: Long = -1,
                               val created: Calendar, val starred: Calendar?)
