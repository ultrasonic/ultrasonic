package org.moire.ultrasonic.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.moire.ultrasonic.domain.Album
import java.util.Date
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder

/**
 * This database is used to store and cache the ID3 metadata
 */

@Database(
    entities = [Artist::class, Album::class, Index::class, MusicFolder::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MetaDatabase : RoomDatabase() {
    abstract fun artistsDao(): ArtistsDao

    abstract fun albumDao(): AlbumDao

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

// FIXME: Check if correct
val META_MIGRATION_2_1: Migration = object : Migration(2, 1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "DROP TABLE ServerSetting"
        )
    }
}
