package org.moire.ultrasonic.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Provides cached drawables for the UI
 */
class ImageHelper(context: Context) {

    lateinit var errorImage: Drawable
    lateinit var starHollowDrawable: Drawable
    lateinit var starDrawable: Drawable
    lateinit var pinImage: Drawable
    lateinit var downloadedImage: Drawable
    lateinit var downloadingImage: Drawable
    lateinit var playingImage: Drawable
    var theme: String

    fun rebuild(context: Context, force: Boolean = false) {
        val currentTheme = Settings.theme!!
        val themesMatch = theme == currentTheme
        if (!themesMatch) theme = currentTheme

        if (!themesMatch || force ) {
            getDrawables(context)
        }
    }

    init {
        theme = Settings.theme!!
        getDrawables(context)
    }

    private fun getDrawables(context: Context) {
        starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow)
        starDrawable = Util.getDrawableFromAttribute(context, R.attr.star_full)
        pinImage = Util.getDrawableFromAttribute(context, R.attr.pin)
        downloadedImage = Util.getDrawableFromAttribute(context, R.attr.downloaded)
        errorImage = Util.getDrawableFromAttribute(context, R.attr.error)
        downloadingImage = Util.getDrawableFromAttribute(context, R.attr.downloading)
        playingImage = Util.getDrawableFromAttribute(context, R.attr.media_play_small)
    }
}
