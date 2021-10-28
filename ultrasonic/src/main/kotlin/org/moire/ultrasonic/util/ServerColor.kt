/*
 * ServerColor.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.moire.ultrasonic.R

private const val LUMINANCE_LIMIT = 0.4

/**
 * Contains functions for computing server display colors
 */
object ServerColor {
    fun getBackgroundColor(context: Context, serverColor: Int?): Int {
        return serverColor ?: ContextCompat.getColor(
            context, Util.getResourceFromAttribute(context, R.attr.colorPrimary)
        )
    }

    fun getForegroundColor(context: Context, serverColor: Int?): Int {
        if (serverColor == null) return ContextCompat.getColor(
            context, Util.getResourceFromAttribute(context, R.attr.colorOnPrimary)
        )
        val luminance = ColorUtils.calculateLuminance(serverColor)
        return if (luminance < LUMINANCE_LIMIT) {
            ContextCompat.getColor(context, R.color.selected_menu_dark)
        } else {
            ContextCompat.getColor(context, R.color.selected_menu_light)
        }
    }
}
