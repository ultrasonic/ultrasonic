package org.moire.ultrasonic.subsonic

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.imageloader.ImageLoaderConfig
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util

/**
 * Handles the lifetime of the Image Loader
 */
class ImageLoaderProvider(val context: Context) : KoinComponent {
    private var imageLoader: ImageLoader? = null
    private var serverID: String = get(named("ServerID"))

    @Synchronized
    fun clearImageLoader() {
        imageLoader = null
    }

    @Synchronized
    fun getImageLoader(): ImageLoader {
        // We need to generate a new ImageLoader if the server has changed...
        val currentID = get<String>(named("ServerID"))
        if (imageLoader == null || currentID != serverID) {
            imageLoader = get()
            serverID = currentID
        }
        return imageLoader!!
    }

    companion object {
        val config by lazy {
            var defaultSize = 0
            val fallbackImage = ResourcesCompat.getDrawable(
                UApp.applicationContext().resources, R.drawable.unknown_album, null
            )

            // Determine the density-dependent image sizes by taking the fallback album
            // image and querying its size.
            if (fallbackImage != null) {
                defaultSize = fallbackImage.intrinsicHeight
            }

            ImageLoaderConfig(
                Util.getMaxDisplayMetric(),
                defaultSize,
                FileUtil.albumArtDirectory
            )
        }
    }
}
