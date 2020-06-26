package org.moire.ultrasonic.app

import androidx.multidex.MultiDexApplication
import org.koin.android.ext.android.startKoin
import org.moire.ultrasonic.di.*

class UApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        startKoin(
            this,
            listOf(
                directoriesModule,
                appPermanentStorage,
                baseNetworkModule,
                featureFlagsModule,
                musicServiceModule,
                mediaPlayerModule
            ),
            extraProperties = mapOf(
                DiProperties.APP_CONTEXT to applicationContext
            )
        )
    }
}
