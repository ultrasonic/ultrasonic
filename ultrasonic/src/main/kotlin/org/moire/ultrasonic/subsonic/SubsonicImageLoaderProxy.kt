package org.moire.ultrasonic.subsonic

import android.view.View
import android.widget.ImageView
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.util.LegacyImageLoader

/**
 * Temporary proxy between new [SubsonicImageLoader] and [ImageLoader] interface and old
 * [LegacyImageLoader] implementation.
 *
 * Should be removed on [LegacyImageLoader] removal.
 */
class SubsonicImageLoaderProxy(
    legacyImageLoader: LegacyImageLoader,
    private val subsonicImageLoader: SubsonicImageLoader
) : ImageLoader by legacyImageLoader {
    override fun loadImage(
        view: View?,
        entry: MusicDirectory.Entry?,
        large: Boolean,
        size: Int,
        crossFade: Boolean,
        highQuality: Boolean
    ) {
        val id = entry?.coverArt

        if (id != null &&
            view != null &&
            view is ImageView) {
            subsonicImageLoader.loadCoverArt(
                entityId = id,
                view = view
            )
        }
    }
}
