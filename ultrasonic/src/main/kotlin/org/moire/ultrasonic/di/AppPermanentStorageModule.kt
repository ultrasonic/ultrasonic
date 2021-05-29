package org.moire.ultrasonic.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.data.AppDatabase
import org.moire.ultrasonic.data.MIGRATION_1_2
import org.moire.ultrasonic.fragment.ServerSettingsModel
import org.moire.ultrasonic.util.Util

const val SP_NAME = "Default_SP"

/**
 * This Koin module contains registration of classes related to permanent storage
 */
val appPermanentStorage = module {
    single(named(SP_NAME)) { Util.getPreferences() }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "ultrasonic-database"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    single { get<AppDatabase>().serverSettingDao() }

    viewModel { ServerSettingsModel(get(), get(), get()) }
}
