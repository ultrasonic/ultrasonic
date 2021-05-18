package org.moire.ultrasonic.domain

/**
 * Represents a top level directory in which music or other media is stored.
 */
data class MusicFolder(
    override val id: String,
    override val name: String
) : GenericEntry()
