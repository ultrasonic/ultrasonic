package org.moire.ultrasonic.di

import org.koin.dsl.module.module
import org.moire.ultrasonic.featureflags.FeatureStorage

val featureFlagsModule = module {
    factory { FeatureStorage(getProperty(DiProperties.APP_CONTEXT)) }
}
