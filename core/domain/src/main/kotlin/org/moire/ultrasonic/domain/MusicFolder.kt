package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a top level directory in which music or other media is stored.
 */
@Entity(tableName = "music_folders")
data class MusicFolder(
    @PrimaryKey override val id: String,
    override val name: String
) : GenericEntry(id)
