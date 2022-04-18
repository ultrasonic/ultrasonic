package org.moire.ultrasonic.data

import androidx.room.Query
import org.moire.ultrasonic.domain.MusicDirectory

interface TrackDao {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM tracks")
    fun clear()

    /**
     * Get all albums
     */
    @Query("SELECT * FROM tracks")
    fun get(): List<MusicDirectory.Album>

    /**
     * Get albums by artist
     */
    @Query("SELECT * FROM tracks WHERE albumId LIKE :id")
    fun byAlbum(id: String): List<MusicDirectory.Entry>

}