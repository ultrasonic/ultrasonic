package org.moire.ultrasonic.subsonic

import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.subsonic.loader.image.ImageRequest
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader
import org.moire.ultrasonic.util.ImageLoader
import org.moire.ultrasonic.util.LegacyImageLoader
import org.moire.ultrasonic.util.Util

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

    private var imageSizeLarge = Util.getMaxDisplayMetric()
    private var imageSizeDefault = 0


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
        var requestedSize = size
        val unknownImageId =
            if (defaultResourceId == -1) R.drawable.unknown_album
            else defaultResourceId

        if (requestedSize <= 0) {
            requestedSize = if (large) imageSizeLarge else imageSizeDefault
        }

        if (id != null && id.isNotEmpty() && view is ImageView) {
            val request = ImageRequest.CoverArt(
                id, view, requestedSize,
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
        if (username != null && username.isNotEmpty() && view is ImageView) {
            val request = ImageRequest.Avatar(
                username, view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            subsonicImageLoader.load(request)
        }
    }

    init {
        val default = ResourcesCompat.getDrawable(
            UApp.applicationContext().resources, R.drawable.unknown_album, null)

        // Determine the density-dependent image sizes by taking the fallback album
        // image and querying its size.
        if (default != null) {
            imageSizeDefault = default.intrinsicHeight
        }
    }
}
