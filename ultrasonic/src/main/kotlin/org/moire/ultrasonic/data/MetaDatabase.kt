package org.moire.ultrasonic.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.Track

/**
 * This database is used to store and cache the ID3 metadata
 */

@Database(
    entities = [
        Artist::class,
        Album::class,
        Track::class,
        Index::class,
        MusicFolder::class
    ],
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2
        ),
    ],
    exportSchema = true,
    version = 2
)
@TypeConverters(Converters::class)
abstract class MetaDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao

    abstract fun albumDao(): AlbumDao

    abstract fun trackDao(): TrackDao

    abstract fun musicFoldersDao(): MusicFoldersDao

    abstract fun indexDao(): IndexDao
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
