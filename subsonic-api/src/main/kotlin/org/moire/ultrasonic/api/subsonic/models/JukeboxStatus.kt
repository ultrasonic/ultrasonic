package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class JukeboxStatus(
    val currentIndex: Int = -1,
    val playing: Boolean = false,
    val gain: Float = 0.0f,
    val position: Int = 0,
    @JsonProperty("entry") val playlistEntries: List<MusicDirectoryChild> = emptyList()
)
