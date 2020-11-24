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
        return loadImage(view, entry, large, size, crossFade, highQuality, -1)
    }

    override fun loadImage(
        view: View?,
        entry: MusicDirectory.Entry?,
        large: Boolean,
        size: Int,
        crossFade: Boolean,
        highQuality: Boolean,
        defaultResourceId: Int
    ) {
        val id = entry?.coverArt
        val unknownImageId =
            if (defaultResourceId == -1) R.drawable.unknown_album
            else defaultResourceId

        if (id != null &&
            view != null &&
            view is ImageView
        ) {
            val request = ImageRequest.CoverArt(
                id, view,
                placeHolderDrawableRes = unknownImageId,
                errorDrawableRes = unknownImageId
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
                username, view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            subsonicImageLoader.load(request)
        }
    }
}
