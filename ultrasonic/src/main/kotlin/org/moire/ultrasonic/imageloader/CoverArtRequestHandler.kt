package org.moire.ultrasonic.imageloader

import com.squareup.picasso.Picasso.LoadedFrom.DISK
import com.squareup.picasso.Picasso.LoadedFrom.NETWORK
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import java.io.IOException
import okio.Okio
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.util.FileUtil.SUFFIX_LARGE
import org.moire.ultrasonic.util.FileUtil.SUFFIX_SMALL

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
        // Note: Currently we are only caching full size images on disk
        // So we modify the key to query for the full size image,
        // because scaling down a larger size image on the device is quicker than
        // requesting the down-sized image from the network.
        val key = request.stableKey!!.replace(SUFFIX_SMALL, SUFFIX_LARGE)
        val cache = BitmapUtils.getAlbumArtBitmapFromDisk(key, size?.toInt())
        if (cache != null) {
            return Result(cache, DISK)
        }

        // Try to fetch the image from the API
        val response = apiClient.getCoverArt(id, size)
        if (!response.hasError() && response.stream != null) {
            return Result(Okio.source(response.stream!!), NETWORK)
        }

        // Throw an error if still not successful
        throw IOException("${response.apiError}")
    }
}
