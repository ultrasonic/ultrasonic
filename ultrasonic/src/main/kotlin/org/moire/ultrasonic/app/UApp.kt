package org.moire.ultrasonic.app

import android.app.Application
import org.koin.android.ext.android.get
import org.koin.android.ext.android.startKoin
import org.moire.ultrasonic.di.DiProperties
import org.moire.ultrasonic.di.appPermanentStorage
import org.moire.ultrasonic.di.baseNetworkModule
import org.moire.ultrasonic.di.directoriesModule
import org.moire.ultrasonic.di.featureFlagsModule
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader

class UApp : Application() {
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

    /**
     * Temporary method to get subsonic image loader from java code.
     */
    fun getSubsonicImageLoader(): SubsonicImageLoader {
        return get()
    }

    /**
     * Temporary method to get features storage.
     */
    fun getFeaturesStorage(): FeatureStorage {
        return get()
    }
}
