package org.moire.ultrasonic.subsonic

import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.moire.ultrasonic.featureflags.Feature
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.util.LegacyImageLoader
import org.moire.ultrasonic.util.Util

/**
 * Handles the lifetime of the Image Loader
 */
class ImageLoaderProvider(val context: Context) : KoinComponent {
    private var imageLoader: ImageLoader? = null

    @Synchronized
    fun clearImageLoader() {
        if (
            imageLoader != null &&
            imageLoader!!.isRunning
        ) {
            imageLoader!!.clear()
        }
        imageLoader = null
    }

    @Synchronized
    fun getImageLoader(): ImageLoader {
        if (imageLoader == null || !imageLoader!!.isRunning) {
            val legacyImageLoader = LegacyImageLoader(
                context,
                Util.getImageLoaderConcurrency()
            )
            val features: FeatureStorage = get()
            val isNewImageLoaderEnabled = features.isFeatureEnabled(Feature.NEW_IMAGE_DOWNLOADER)
            imageLoader = if (isNewImageLoaderEnabled) {
                SubsonicImageLoaderProxy(
                    legacyImageLoader,
                    get()
                )
            } else {
                legacyImageLoader
            }
            imageLoader!!.startImageLoader()
        }
        return imageLoader!!
    }
}
