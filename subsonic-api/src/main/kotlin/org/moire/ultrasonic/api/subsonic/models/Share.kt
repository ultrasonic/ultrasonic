package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Share(
        val id: String = "",
        val url: String = "",
        val username: String = "",
        val created: Calendar? = null,
        val expires: Calendar? = null,
        val visitCount: Int = 0,
        val description: String = "",
        val lastVisited: Calendar? = null,
        @JsonProperty("entry") val items: List<MusicDirectoryChild> = emptyList())
