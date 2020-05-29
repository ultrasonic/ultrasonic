package org.moire.ultrasonic.app

import android.app.Application
import androidx.multidex.MultiDexApplication
import org.koin.android.ext.android.startKoin
import org.moire.ultrasonic.di.DiProperties
import org.moire.ultrasonic.di.appPermanentStorage
import org.moire.ultrasonic.di.baseNetworkModule
import org.moire.ultrasonic.di.directoriesModule
import org.moire.ultrasonic.di.featureFlagsModule
import org.moire.ultrasonic.di.musicServiceModule

class UApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        startKoin(this,
            listOf(
                directoriesModule,
                appPermanentStorage,
                baseNetworkModule,
                featureFlagsModule,
                musicServiceModule
            ),
            extraProperties = mapOf(
                DiProperties.APP_CONTEXT to applicationContext
            )
        )
    }
}
