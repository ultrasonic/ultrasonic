package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResult(
    val offset: Int = 0,
    val totalHits: Int = 0,
    @JsonProperty("match") val matchList: List<MusicDirectoryChild> = emptyList()
)
