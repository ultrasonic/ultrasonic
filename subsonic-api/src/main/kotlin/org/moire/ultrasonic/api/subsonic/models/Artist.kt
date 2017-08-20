package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Calendar

data class Artist(val id: Long = -1,
                  val name: String = "",
                  val coverArt: String = "",
                  val albumCount: Int = 0,
                  val starred: Calendar? = null,
                  @JsonProperty("album")
                  val albumsList: List<Album> = emptyList())
