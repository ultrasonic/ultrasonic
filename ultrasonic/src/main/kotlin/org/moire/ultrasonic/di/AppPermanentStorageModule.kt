package org.moire.ultrasonic.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.fragment.ServerSettingsModel
import org.moire.ultrasonic.data.AppDatabase
import org.moire.ultrasonic.data.MIGRATION_1_2
import org.moire.ultrasonic.util.Util

const val SP_NAME = "Default_SP"

val appPermanentStorage = module {
    single(named(SP_NAME)) { Util.getPreferences(androidContext()) }

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

    viewModel { ServerSettingsModel(get(), get(), androidContext()) }
}
