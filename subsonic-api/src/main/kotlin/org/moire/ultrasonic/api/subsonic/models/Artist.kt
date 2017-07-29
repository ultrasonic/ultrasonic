package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class Artist(val id: Long,
                  val name: String,
                  val starred: Calendar?)