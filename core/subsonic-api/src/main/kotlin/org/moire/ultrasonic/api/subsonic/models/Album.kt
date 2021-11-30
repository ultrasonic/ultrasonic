package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Album(
    val id: String = "",
    val parent: String = "",
    val album: String = "",
    val title: String? = null,
    val name: String? = null,
    val discNumber: Int = 0,
    val coverArt: String = "",
    val songCount: Int = 0,
    val created: Calendar? = null,
    val artist: String = "",
    val artistId: String = "",
    val duration: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val playCount: Int = 0,
    @JsonProperty("song") val songList: List<MusicDirectoryChild> = emptyList(),
    @JsonProperty("starred") val starredDate: String = ""
)
