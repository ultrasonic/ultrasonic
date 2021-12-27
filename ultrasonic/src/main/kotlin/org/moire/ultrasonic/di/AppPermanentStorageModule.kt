package org.moire.ultrasonic.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.data.AppDatabase
import org.moire.ultrasonic.data.MIGRATION_1_2
import org.moire.ultrasonic.data.MIGRATION_2_1
import org.moire.ultrasonic.data.MIGRATION_2_3
import org.moire.ultrasonic.data.MIGRATION_3_2
import org.moire.ultrasonic.data.MIGRATION_3_4
import org.moire.ultrasonic.data.MIGRATION_4_3
import org.moire.ultrasonic.fragment.ServerSettingsModel
import org.moire.ultrasonic.util.Settings

const val SP_NAME = "Default_SP"
const val DB_FILENAME = "ultrasonic-database"

/**
 * This Koin module contains registration of classes related to permanent storage
 */
val appPermanentStorage = module {
    single(named(SP_NAME)) { Settings.preferences }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            DB_FILENAME
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_1)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_2)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_3)
            .build()
    }

    single { get<AppDatabase>().serverSettingDao() }

    viewModel { ServerSettingsModel(get(), get(), get()) }
}
