package org.moire.ultrasonic.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.moire.ultrasonic.featureflags.FeatureStorage

val featureFlagsModule = module {
    factory { FeatureStorage(androidContext()) }
}
