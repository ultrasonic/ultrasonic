package org.moire.ultrasonic.subsonic

import android.view.View
import android.widget.ImageView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.subsonic.loader.image.ImageRequest
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
            view is ImageView
        ) {
            val request = ImageRequest.CoverArt(
                id,
                view,
                placeHolderDrawableRes = R.drawable.unknown_album,
                errorDrawableRes = R.drawable.unknown_album
            )
            subsonicImageLoader.load(request)
        }
    }

    override fun loadAvatarImage(
        view: View?,
        username: String?,
        large: Boolean,
        size: Int,
        crossFade: Boolean,
        highQuality: Boolean
    ) {
        if (username != null &&
            view != null &&
            view is ImageView
        ) {
            val request = ImageRequest.Avatar(
                username,
                view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            subsonicImageLoader.load(request)
        }
    }
}
