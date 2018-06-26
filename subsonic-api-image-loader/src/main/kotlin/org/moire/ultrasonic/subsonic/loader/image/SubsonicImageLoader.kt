package org.moire.ultrasonic.subsonic.loader.image

import android.content.Context
import android.widget.ImageView
import com.squareup.picasso.Picasso
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

class SubsonicImageLoader(
    private val context: Context,
    apiClient: SubsonicAPIClient
) {
    private val picasso = Picasso.Builder(context)
            .addRequestHandler(CoverArtRequestHandler(apiClient))
            .build().apply { setIndicatorsEnabled(BuildConfig.DEBUG) }

    fun loadCoverArt(entityId: String, view: ImageView) {
        picasso.load(createLoadCoverArtRequest(entityId))
                .into(view)
    }
}
