package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder

@Database(
    entities = [Artist::class, Index::class, MusicFolder::class],
    version = 1
)
abstract class MetaDatabase : RoomDatabase() {
    abstract fun artistsDao(): ArtistsDao

    abstract fun musicFoldersDao(): MusicFoldersDao

    abstract fun indexDao(): IndexDao
}
