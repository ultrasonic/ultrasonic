package org.moire.ultrasonic.subsonic.loader.image

import android.content.Context
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

// TODO: Caching doesn't work as expected because our query string varies.
// Need to use .stableKey() method

class SubsonicImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient
) {
    private val picasso = Picasso.Builder(context)
        .addRequestHandler(CoverArtRequestHandler(apiClient))
        .addRequestHandler(AvatarRequestHandler(apiClient))
        .build().apply { setIndicatorsEnabled(true) }

    fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
        is ImageRequest.Avatar -> loadAvatar(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request))
            .addPlaceholder(request)
            .addError(request)
            .stableKey("${request.entityId}-${request.size}" )
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
}

sealed class ImageRequest(
    val placeHolderDrawableRes: Int? = null,
    val errorDrawableRes: Int? = null,
    val imageView: ImageView
) {
    class CoverArt(
        val entityId: String,
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
