package org.moire.ultrasonic.imageloader

import android.content.Context
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

class SubsonicImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient
) {
    private val picasso = Picasso.Builder(context)
        .addRequestHandler(CoverArtRequestHandler(apiClient))
        .addRequestHandler(AvatarRequestHandler(apiClient))
        .build().apply {
            setIndicatorsEnabled(BuildConfig.DEBUG)
            Picasso.setSingletonInstance(this)
        }

    fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
        is ImageRequest.Avatar -> loadAvatar(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request.entityId, request.size.toLong()))
            .addPlaceholder(request)
            .addError(request)
            .stableKey("${request.entityId}-${request.size}")
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
