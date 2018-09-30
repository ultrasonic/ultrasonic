package org.moire.ultrasonic.subsonic.loader.image

import com.squareup.picasso.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import okio.Okio
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import java.io.IOException

/**
 * Loads cover arts from subsonic api.
 */
class CoverArtRequestHandler(private val apiClient: SubsonicAPIClient) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME &&
                    authority == AUTHORITY &&
                    path == "/$COVER_ART_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val id = request.uri.getQueryParameter(QUERY_ID) ?: throw IllegalArgumentException("Nullable id")

        val response = apiClient.getCoverArt(id)
        if (response.hasError()) {
            throw IOException("${response.apiError}")
        } else {
            return Result(Okio.source(response.stream), NETWORK)
        }
    }
}
