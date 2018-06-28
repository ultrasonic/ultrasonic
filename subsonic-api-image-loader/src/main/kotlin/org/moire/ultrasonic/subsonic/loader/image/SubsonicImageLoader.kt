package org.moire.ultrasonic.subsonic.loader.image

import android.content.Context
import android.widget.ImageView
import com.squareup.picasso.Picasso
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

class SubsonicImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient
) {
    private val picasso = Picasso.Builder(context)
            .addRequestHandler(CoverArtRequestHandler(apiClient))
            .build().apply { setIndicatorsEnabled(BuildConfig.DEBUG) }

    fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request.entityId))
            .apply {
                if (request.placeHolderDrawableRes != null) {
                    placeholder(request.placeHolderDrawableRes)
                }
            }
            .apply {
                if (request.errorDrawableRes != null) {
                    error(request.errorDrawableRes)
                }
            }
            .into(request.imageView)
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
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )
}
