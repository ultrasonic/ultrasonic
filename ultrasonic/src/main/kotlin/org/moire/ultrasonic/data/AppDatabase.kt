package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database to be used to store data for Ultrasonic
 */
@Database(entities = [ServerSetting::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Retrieves the Server Settings DAO for the Database
     */
    abstract fun serverSettingDao(): ServerSettingDao
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN minimumApiVersion TEXT"
        )
    }
}
