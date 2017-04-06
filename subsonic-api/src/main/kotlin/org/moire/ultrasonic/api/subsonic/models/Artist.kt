package org.moire.ultrasonic.api.subsonic.models

import java.util.*

data class Artist(val id: Long,
                  val name: String,
                  val starred: Calendar?)