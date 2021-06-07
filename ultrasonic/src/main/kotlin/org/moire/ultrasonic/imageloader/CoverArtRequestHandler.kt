package org.moire.ultrasonic.imageloader

import com.squareup.picasso.Picasso.LoadedFrom.DISK
import com.squareup.picasso.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import java.io.IOException
import okio.Okio
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

/**
 * Loads cover arts from subsonic api.
 */
class CoverArtRequestHandler(private val apiClient: SubsonicAPIClient) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME &&
                path == "/$COVER_ART_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val id = request.uri.getQueryParameter(QUERY_ID)
            ?: throw IllegalArgumentException("Nullable id")
        val size = request.uri.getQueryParameter(SIZE)?.toLong()

        // Check if we have a hit in the disk cache
        val cache = BitmapUtils.getAlbumArtBitmapFromDisk(request.stableKey!!, size?.toInt())
        if (cache != null) {
            return Result(cache, DISK)
        }

        val response = apiClient.getCoverArt(id, size)
        if (response.hasError() || response.stream == null) {
            throw IOException("${response.apiError}")
        } else {
            return Result(Okio.source(response.stream!!), NETWORK)
        }
    }
}
