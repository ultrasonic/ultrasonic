package org.moire.ultrasonic.app

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.di.appPermanentStorage
import org.moire.ultrasonic.di.applicationModule
import org.moire.ultrasonic.di.baseNetworkModule
import org.moire.ultrasonic.di.featureFlagsModule
import org.moire.ultrasonic.di.mediaPlayerModule
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.log.FileLoggerTree
import org.moire.ultrasonic.log.TimberKoinLogger
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.StorageFile
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * The Main class of the Application
 */

class UApp : MultiDexApplication() {

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        if (Settings.debugLogToFile) {
            FileLoggerTree.plantToTimberForest()
        }

        startKoin {
            logger(TimberKoinLogger(Level.INFO))
            // declare Android context
            androidContext(this@UApp)
            // declare modules to use
            modules(
                applicationModule,
                appPermanentStorage,
                baseNetworkModule,
                featureFlagsModule,
                musicServiceModule,
                mediaPlayerModule
            )
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener())
    }

    companion object {
        private var instance: UApp? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }
}

class AppLifecycleListener : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onMoveToForeground() {
        StorageFile.resetCaches()
    }
}