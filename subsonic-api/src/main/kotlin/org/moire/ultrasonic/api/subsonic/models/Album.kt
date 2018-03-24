package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Album(
    val id: String = "",
    val name: String = "",
    val coverArt: String = "",
    val artist: String = "",
    val artistId: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: Calendar? = null,
    val year: Int = 0,
    val genre: String = "",
    @JsonProperty("song") val songList: List<MusicDirectoryChild> = emptyList()
)
