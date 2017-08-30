package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Playlist(
        val id: Long = -1,
        val name: String = "",
        val owner: String = "",
        val public: Boolean = false,
        val songCount: Int = 0,
        val duration: Long = 0,
        val created: Calendar? = null,
        val changed: Calendar? = null,
        val coverArt: String = "",
        @JsonProperty("entry") val entriesList: List<MusicDirectoryChild> = emptyList()
)