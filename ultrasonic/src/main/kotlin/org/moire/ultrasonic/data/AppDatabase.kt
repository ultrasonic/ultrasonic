package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room Database to be used to store data for Ultrasonic
 */
@Database(entities = [ServerSetting::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Retrieves the Server Settings DAO for the Database
     */
    abstract fun serverSettingDao(): ServerSettingDao
}
