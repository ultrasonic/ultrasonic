package org.moire.ultrasonic.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database to be used to store global data for the whole app.
 * This could be settings or data that are not specific to any remote music database
 */
@Database(
        entities = [ServerSetting::class],
        version = 5,
        exportSchema = true
)
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

val MIGRATION_2_1: Migration = object : Migration(2, 1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS ServerSettingMigration (
                    id INTEGER NOT NULL PRIMARY KEY,
                    [index] INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    userName TEXT NOT NULL,
                    password TEXT NOT NULL,
                    jukeboxByDefault INTEGER NOT NULL,
                    allowSelfSignedCertificate INTEGER NOT NULL,
                    ldapSupport INTEGER NOT NULL,
                    musicFolderId TEXT
                )
            """.trimIndent()
        )
        database.execSQL(
            """
                INSERT INTO ServerSettingMigration (
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId
                )
                SELECT
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId
                FROM ServerSetting
            """.trimIndent()
        )
        database.execSQL(
            "DROP TABLE ServerSetting"
        )
        database.execSQL(
            "ALTER TABLE ServerSettingMigration RENAME TO ServerSetting"
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

val MIGRATION_3_2: Migration = object : Migration(3, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS ServerSettingMigration (
                    id INTEGER NOT NULL PRIMARY KEY,
                    [index] INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    userName TEXT NOT NULL,
                    password TEXT NOT NULL,
                    jukeboxByDefault INTEGER NOT NULL,
                    allowSelfSignedCertificate INTEGER NOT NULL,
                    ldapSupport INTEGER NOT NULL,
                    musicFolderId TEXT,
                    minimumApiVersion TEXT
                )
            """.trimIndent()
        )
        database.execSQL(
            """
                INSERT INTO ServerSettingMigration (
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId, minimumApiVersion
                )
                SELECT
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId, minimumApiVersion
                FROM ServerSetting
            """.trimIndent()
        )
        database.execSQL(
            "DROP TABLE ServerSetting"
        )
        database.execSQL(
            "ALTER TABLE ServerSettingMigration RENAME TO ServerSetting"
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

val MIGRATION_4_3: Migration = object : Migration(4, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS ServerSettingMigration (
                    id INTEGER NOT NULL PRIMARY KEY,
                    [index] INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    userName TEXT NOT NULL,
                    password TEXT NOT NULL,
                    jukeboxByDefault INTEGER NOT NULL,
                    allowSelfSignedCertificate INTEGER NOT NULL,
                    ldapSupport INTEGER NOT NULL,
                    musicFolderId TEXT,
                    minimumApiVersion TEXT,
                    chatSupport INTEGER,
                    bookmarkSupport INTEGER,
                    shareSupport INTEGER,
                    podcastSupport INTEGER
                )
            """.trimIndent()
        )
        database.execSQL(
            """
                INSERT INTO ServerSettingMigration (
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId, minimumApiVersion,
                    chatSupport, bookmarkSupport, shareSupport, podcastSupport
                )
                SELECT
                    id, [index], name, url, userName, password, jukeboxByDefault,
                    allowSelfSignedCertificate, ldapSupport, musicFolderId, minimumApiVersion,
                    chatSupport, bookmarkSupport, shareSupport, podcastSupport
                FROM ServerSetting
            """.trimIndent()
        )
        database.execSQL(
            "DROP TABLE ServerSetting"
        )
        database.execSQL(
            "ALTER TABLE ServerSettingMigration RENAME TO ServerSetting"
        )
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS `_new_ServerSetting` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `index` INTEGER NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `url` TEXT NOT NULL, 
                    `color` INTEGER, 
                    `userName` TEXT NOT NULL, 
                    `password` TEXT NOT NULL, 
                    `jukeboxByDefault` INTEGER NOT NULL, 
                    `allowSelfSignedCertificate` INTEGER NOT NULL, 
                    `ldapSupport` INTEGER NOT NULL, 
                    `musicFolderId` TEXT, 
                    `minimumApiVersion` TEXT, 
                    `chatSupport` INTEGER, 
                    `bookmarkSupport` INTEGER, 
                    `shareSupport` INTEGER, 
                    `podcastSupport` INTEGER
                    )
            """.trimIndent()
        )
        database.execSQL(
            """
                    INSERT INTO `_new_ServerSetting` (
                    `ldapSupport`,`musicFolderId`,`color`,`index`,`userName`,`minimumApiVersion`,
                    `jukeboxByDefault`,`url`,`password`,`shareSupport`,`bookmarkSupport`,`name`,
                    `podcastSupport`,`id`,`allowSelfSignedCertificate`,`chatSupport`
                    ) 
                    SELECT `ldapSupport`,`musicFolderId`,`color`,`index`,`userName`,
                    `minimumApiVersion`,`jukeboxByDefault`,`url`,`password`,`shareSupport`,
                    `bookmarkSupport`,`name`,`podcastSupport`,`id`,`allowSelfSignedCertificate`,
                    `chatSupport` 
                    FROM `ServerSetting`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `ServerSetting`")
        database.execSQL("ALTER TABLE `_new_ServerSetting` RENAME TO `ServerSetting`")
    }
}

val MIGRATION_5_4: Migration = object : Migration(5, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                    CREATE TABLE IF NOT EXISTS `_new_ServerSetting` (
                    `id` INTEGER PRIMARY KEY NOT NULL, 
                    `index` INTEGER NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `url` TEXT NOT NULL, 
                    `color` INTEGER, 
                    `userName` TEXT NOT NULL, 
                    `password` TEXT NOT NULL, 
                    `jukeboxByDefault` INTEGER NOT NULL, 
                    `allowSelfSignedCertificate` INTEGER NOT NULL, 
                    `ldapSupport` INTEGER NOT NULL, 
                    `musicFolderId` TEXT, 
                    `minimumApiVersion` TEXT, 
                    `chatSupport` INTEGER, 
                    `bookmarkSupport` INTEGER, 
                    `shareSupport` INTEGER, 
                    `podcastSupport` INTEGER
                    )
            """.trimIndent()
        )
        database.execSQL(
            """
                    INSERT INTO `_new_ServerSetting` (
                    `ldapSupport`,`musicFolderId`,`color`,`index`,`userName`,`minimumApiVersion`,
                    `jukeboxByDefault`,`url`,`password`,`shareSupport`,`bookmarkSupport`,`name`,
                    `podcastSupport`,`id`,`allowSelfSignedCertificate`,`chatSupport`
                    ) 
                    SELECT `ldapSupport`,`musicFolderId`,`color`,`index`,`userName`,
                    `minimumApiVersion`,`jukeboxByDefault`,`url`,`password`,`shareSupport`,
                    `bookmarkSupport`,`name`,`podcastSupport`,`id`,`allowSelfSignedCertificate`,
                    `chatSupport` 
                    FROM `ServerSetting`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `ServerSetting`")
        database.execSQL("ALTER TABLE `_new_ServerSetting` RENAME TO `ServerSetting`")
    }
}
