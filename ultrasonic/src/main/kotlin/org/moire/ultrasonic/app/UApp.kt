package org.moire.ultrasonic.app

import android.app.Application
import org.koin.android.ext.android.startKoin
import org.moire.ultrasonic.di.baseNetworkModule
import org.moire.ultrasonic.di.directoriesModule
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.util.Util

class UApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val sharedPreferences = Util.getPreferences(this)
        startKoin(this, listOf(
            directoriesModule,
            baseNetworkModule,
            musicServiceModule(sharedPreferences)
        ))
    }
}
