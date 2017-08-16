package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class Artist(val id: Long = -1,
                  val name: String = "",
                  val coverArt: String = "",
                  val albumCount: Int = 0,
                  val starred: Calendar? = null)
