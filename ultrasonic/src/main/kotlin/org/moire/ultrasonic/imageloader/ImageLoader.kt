package org.moire.ultrasonic.imageloader

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import java.io.File
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.FileUtil

/**
 * Our new image loader which uses Picasso as a backend.
 */
class ImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient,
    private val config: ImageLoaderConfig
) {

    private val picasso = Picasso.Builder(context)
        .addRequestHandler(CoverArtRequestHandler(apiClient))
        .addRequestHandler(AvatarRequestHandler(apiClient))
        .build().apply {
            setIndicatorsEnabled(BuildConfig.DEBUG)
        }

    private fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
        is ImageRequest.Avatar -> loadAvatar(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request.entityId, request.size.toLong()))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.cacheKey)
            .into(request.imageView)
    }

    private fun loadAvatar(request: ImageRequest.Avatar) {
        picasso.load(createLoadAvatarRequest(request.username))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.username)
            .into(request.imageView)
    }

    private fun RequestCreator.addPlaceholder(request: ImageRequest): RequestCreator {
        if (request.placeHolderDrawableRes != null) {
            placeholder(request.placeHolderDrawableRes)
        }

        return this
    }

    private fun RequestCreator.addError(request: ImageRequest): RequestCreator {
        if (request.errorDrawableRes != null) {
            error(request.errorDrawableRes)
        }

        return this
    }

    /**
     * Load the cover of a given entry into an ImageView
     */
    @JvmOverloads
    fun loadImage(
        view: View?,
        entry: MusicDirectory.Entry?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ) {
        val id = entry?.coverArt
        val requestedSize = resolveSize(size, large)

        if (id != null && id.isNotEmpty() && view is ImageView) {
            val key = FileUtil.getAlbumArtKey(entry)
            val request = ImageRequest.CoverArt(
                id, key, view, requestedSize,
                placeHolderDrawableRes = defaultResourceId,
                errorDrawableRes = defaultResourceId
            )
            load(request)
        }
    }

    /**
     * Load the avatar of a given user into an ImageView
     */
    fun loadAvatarImage(
        view: ImageView,
        username: String
    ) {
        if (username.isNotEmpty()) {
            val request = ImageRequest.Avatar(
                username, view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            load(request)
        }
    }

    private fun resolveSize(requested: Int, large: Boolean): Int {
        if (requested <= 0) {
            return if (large) config.largeSize else config.defaultSize
        } else {
            return requested
        }
    }
}

/**
 * Data classes to hold all the info we need later on to process the request
 */
sealed class ImageRequest(
    val placeHolderDrawableRes: Int? = null,
    val errorDrawableRes: Int? = null,
    val imageView: ImageView
) {
    class CoverArt(
        val entityId: String,
        val cacheKey: String,
        imageView: ImageView,
        val size: Int,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null,
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )

    class Avatar(
        val username: String,
        imageView: ImageView,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )
}

/**
 * Used to configure an instance of the ImageLoader
 */
data class ImageLoaderConfig(
    val largeSize: Int = 0,
    val defaultSize: Int = 0,
    val cacheFolder: File?
)
