package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchThreeResult(
    @JsonProperty("artist") val artistList: List<Artist> = emptyList(),
    @JsonProperty("album") val albumList: List<Album> = emptyList(),
    @JsonProperty("song") val songList: List<MusicDirectoryChild> = emptyList()
)
