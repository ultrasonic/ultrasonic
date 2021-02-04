package org.moire.ultrasonic.subsonic

import android.content.Context
import org.koin.java.KoinJavaComponent.get
import org.moire.ultrasonic.featureflags.Feature
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.util.LegacyImageLoader
import org.moire.ultrasonic.util.Util

class ImageLoaderProvider (val context: Context) {
    private var imageLoader: ImageLoader? = null

    @Synchronized
    fun clearImageLoader() {
        if (imageLoader != null &&
            imageLoader!!.isRunning) {
            imageLoader!!.clear()
        }
        imageLoader = null
    }

    @Synchronized
    fun getImageLoader(): ImageLoader {
        if (imageLoader == null || !imageLoader!!.isRunning) {
            val legacyImageLoader = LegacyImageLoader(
                context,
                Util.getImageLoaderConcurrency(context)
            )
            val isNewImageLoaderEnabled = get(FeatureStorage::class.java)
                .isFeatureEnabled(Feature.NEW_IMAGE_DOWNLOADER)
            if (isNewImageLoaderEnabled) {
                imageLoader = SubsonicImageLoaderProxy(
                    legacyImageLoader,
                    get(SubsonicImageLoader::class.java)
                )
            } else {
                imageLoader = legacyImageLoader
            }
            imageLoader!!.startImageLoader()
        }
        return imageLoader!!
    }
}