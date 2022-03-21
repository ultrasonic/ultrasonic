/*
 * LocaleHelper.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Simple Helper class to "wrap" a context with a new locale.
 */
class LocaleHelper(base: Context?) : ContextWrapper(base) {
    companion object {
        fun wrap(ctx: Context?, language: String): ContextWrapper {
            var context = ctx
            if (context != null && language != "") {
                val config = context.resources.configuration
                val locale = Locale.forLanguageTag(language)
                Locale.setDefault(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setSystemLocale(config, locale)
                } else {
                    setSystemLocaleLegacy(config, locale)
                }

                config.setLayoutDirection(locale)
                context = context.createConfigurationContext(config)
            }
            return LocaleHelper(context)
        }

        @Suppress("DEPRECATION")
        private fun setSystemLocaleLegacy(config: Configuration, locale: Locale?) {
            config.locale = locale
        }

        @TargetApi(Build.VERSION_CODES.N)
        fun setSystemLocale(config: Configuration, locale: Locale?) {
            config.setLocale(locale)
        }
    }
}
