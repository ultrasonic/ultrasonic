package org.moire.ultrasonic.di

import org.koin.dsl.module.module
import org.moire.ultrasonic.util.Util

const val SP_NAME = "Default_SP"

val appPermanentStorage = module {
    single(name = SP_NAME) { Util.getPreferences(getProperty(DiProperties.APP_CONTEXT)) }
}