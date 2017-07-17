package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class MusicDirectory(val id: Long,
                          val name: String,
                          val starred: Calendar?,
                          @JsonProperty("child")
                          val childList: List<MusicDirectoryChild> = emptyList())