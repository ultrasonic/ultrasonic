package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder

/**
 * This database is used to store and cache the ID3 metadata
 */

@Database(
    entities = [Artist::class, Index::class, MusicFolder::class],
    version = 1,
    exportSchema = true
)
abstract class MetaDatabase : RoomDatabase() {
    abstract fun artistsDao(): ArtistsDao

    abstract fun musicFoldersDao(): MusicFoldersDao

    abstract fun indexDao(): IndexDao
}
