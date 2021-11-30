package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey override var id: String,
    override var name: String? = null,
    override var index: String? = null,
    override var coverArt: String? = null,
    override var albumCount: Long? = null,
    override var closeness: Int = 0
) : ArtistOrIndex(id)
