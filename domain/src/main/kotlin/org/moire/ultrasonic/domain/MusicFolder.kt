package org.moire.ultrasonic.domain

/**
 * Represents a top level directory in which music or other media is stored.
 */
data class MusicFolder(
        val id: String,
        val name: String
) {
    companion object
}
