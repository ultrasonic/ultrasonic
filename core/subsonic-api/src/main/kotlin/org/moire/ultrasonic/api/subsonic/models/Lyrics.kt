package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Lyrics(
    val artist: String = "",
    val title: String = "",
    @JsonProperty("value") val text: String = ""
)
