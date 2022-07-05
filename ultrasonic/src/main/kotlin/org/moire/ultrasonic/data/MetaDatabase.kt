/*
 * MetaDatabase.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3
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

/* ktlint-disable max-line-length */
val META_MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `albums`")
        database.execSQL("DROP TABLE `indexes`")
        database.execSQL("DROP TABLE `artists`")
        database.execSQL("DROP TABLE `tracks`")
        database.execSQL("DROP TABLE `music_folders`")
        database.execSQL("CREATE TABLE IF NOT EXISTS `albums` (`id` TEXT NOT NULL, `serverId` INTEGER NOT NULL DEFAULT -1, `parent` TEXT, `album` TEXT, `title` TEXT, `name` TEXT, `discNumber` INTEGER, `coverArt` TEXT, `songCount` INTEGER, `created` INTEGER, `artist` TEXT, `artistId` TEXT, `duration` INTEGER, `year` INTEGER, `genre` TEXT, `starred` INTEGER NOT NULL, `path` TEXT, `closeness` INTEGER NOT NULL, `isDirectory` INTEGER NOT NULL, `isVideo` INTEGER NOT NULL, PRIMARY KEY(`id`, `serverId`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `indexes` (`id` TEXT NOT NULL, `serverId` INTEGER NOT NULL DEFAULT -1, `name` TEXT, `index` TEXT, `coverArt` TEXT, `albumCount` INTEGER, `closeness` INTEGER NOT NULL, `musicFolderId` TEXT, PRIMARY KEY(`id`, `serverId`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `artists` (`id` TEXT NOT NULL, `serverId` INTEGER NOT NULL DEFAULT -1, `name` TEXT, `index` TEXT, `coverArt` TEXT, `albumCount` INTEGER, `closeness` INTEGER NOT NULL, PRIMARY KEY(`id`, `serverId`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `music_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `serverId` INTEGER NOT NULL DEFAULT -1, PRIMARY KEY(`id`, `serverId`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `serverId` INTEGER NOT NULL DEFAULT -1, `parent` TEXT, `isDirectory` INTEGER NOT NULL, `title` TEXT, `album` TEXT, `albumId` TEXT, `artist` TEXT, `artistId` TEXT, `track` INTEGER, `year` INTEGER, `genre` TEXT, `contentType` TEXT, `suffix` TEXT, `transcodedContentType` TEXT, `transcodedSuffix` TEXT, `coverArt` TEXT, `size` INTEGER, `songCount` INTEGER, `duration` INTEGER, `bitRate` INTEGER, `path` TEXT, `isVideo` INTEGER NOT NULL, `starred` INTEGER NOT NULL, `discNumber` INTEGER, `type` TEXT, `created` INTEGER, `closeness` INTEGER NOT NULL, `bookmarkPosition` INTEGER NOT NULL, `userRating` INTEGER, `averageRating` REAL, `name` TEXT, PRIMARY KEY(`id`, `serverId`))")
    }
}
/* ktlint-enable max-line-length */
