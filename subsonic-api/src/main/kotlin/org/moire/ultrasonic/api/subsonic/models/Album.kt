package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class Album(
        val id: Long = -1L,
        val name: String = "",
        val coverArt: String = "",
        val artist: String = "",
        val artistId: Long = -1L,
        val songCount: Int = 0,
        val duration: Int = 0,
        val created: Calendar? = null,
        val year: Int = 0,
        val genre: String = "")
