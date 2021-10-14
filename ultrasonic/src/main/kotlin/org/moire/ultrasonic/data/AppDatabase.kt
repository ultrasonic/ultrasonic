package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database to be used to store global data for the whole app.
 * This could be settings or data that are not specific to any remote music database
 */
@Database(entities = [ServerSetting::class], version = 4)
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

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN chatSupport INTEGER"
        )
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN bookmarkSupport INTEGER"
        )
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN shareSupport INTEGER"
        )
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN podcastSupport INTEGER"
        )
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE ServerSetting ADD COLUMN color INTEGER"
        )
    }
}
