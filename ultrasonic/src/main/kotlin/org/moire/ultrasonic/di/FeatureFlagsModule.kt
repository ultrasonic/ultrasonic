package org.moire.ultrasonic.di

import android.content.Context
import org.koin.dsl.module.applicationContext
import org.moire.ultrasonic.featureflags.FeatureStorage

fun featureFlagsModule(
    context: Context
) = applicationContext {
    factory { FeatureStorage(context) }
}
