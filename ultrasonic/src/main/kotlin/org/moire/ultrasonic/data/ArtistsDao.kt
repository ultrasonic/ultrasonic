package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.moire.ultrasonic.domain.Artist

@Dao
interface ArtistsDao {
    /**
     * Insert a list in the database. If the item already exists, replace it.
     *
     * @param objects the items to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @JvmSuppressWildcards
    fun set(objects: List<Artist>)

    /**
     * Clear the whole database
     */
    @Query("DELETE FROM artists")
    fun clear()

    /**
     * Get all artists
     */
    @Query("SELECT * FROM artists")
    fun get(): List<Artist>
}
