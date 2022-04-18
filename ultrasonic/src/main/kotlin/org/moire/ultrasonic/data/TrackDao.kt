package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import org.moire.ultrasonic.domain.Track

@Dao
@Entity(tableName = "tracks")
interface TrackDao : GenericDao<Track> {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM tracks")
    fun clear()

    /**
     * Get all albums
     */
    @Query("SELECT * FROM tracks")
    fun get(): List<Track>

    /**
     * Get albums by artist
     */
    @Query("SELECT * FROM tracks WHERE albumId LIKE :id")
    fun byAlbum(id: String): List<Track>
}
