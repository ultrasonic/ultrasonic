package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Index(
    val name: String = "",
    @JsonProperty("artist")
    val artists: List<Artist> = emptyList()
)
