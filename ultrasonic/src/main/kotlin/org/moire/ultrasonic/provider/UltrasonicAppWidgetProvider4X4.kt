/*
 * UltrasonicAppWidgetProvider4X4.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.provider

import org.moire.ultrasonic.R

class UltrasonicAppWidgetProvider4X4 : UltrasonicAppWidgetProvider() {
    companion object {
        @get:Synchronized
        var instance: UltrasonicAppWidgetProvider4X4? = null
            get() {
                if (field == null) {
                    field = UltrasonicAppWidgetProvider4X4()
                }
                return field
            }
            private set
    }

    init {
        layoutId = R.layout.appwidget4x4
    }
}