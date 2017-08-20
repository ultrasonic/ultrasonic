package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class MusicDirectory(val id: Long = -1L,
                          val parent: Long = -1L,
                          val name: String = "",
                          val userRating: Int = 0,
                          val averageRating: Float = 0.0f,
                          val starred: Calendar? = null,
                          val playCount: Int = 0,
                          @JsonProperty("child")
                          val childList: List<MusicDirectoryChild> = emptyList())
