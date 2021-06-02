package org.moire.ultrasonic.subsonic

import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.moire.ultrasonic.util.ImageLoader

/**
 * Handles the lifetime of the Image Loader
 */
class ImageLoaderProvider(val context: Context) : KoinComponent {
    private var imageLoader: ImageLoader? = null

    @Synchronized
    fun clearImageLoader() {
        imageLoader = null
    }

    @Synchronized
    fun getImageLoader(): ImageLoader {
        if (imageLoader == null) {
            imageLoader = SubsonicImageLoaderProxy(get())
        }
        return imageLoader!!
    }
}
