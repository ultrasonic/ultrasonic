package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Album(
    val id: String = "",
    val parent: String = "",
    val album: String = "",
    val title: String = "",
    val name: String = "",
    val discNumber: Int = 0,
    val coverArt: String = "",
    val songCount: Int = 0,
    val created: Calendar? = null,
    val artist: String = "",
    val artistId: String = "",
    val duration: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    @JsonProperty("song") val songList: List<MusicDirectoryChild> = emptyList(),
    @JsonProperty("starred") val starredDate: String = ""
)
