package org.moire.ultrasonic.subsonic.loader.image

import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import okio.Okio
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import java.io.IOException

/**
 * Loads avatars from subsonic api.
 */
class AvatarRequestHandler(
    private val apiClient: SubsonicAPIClient
) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME &&
                authority == AUTHORITY &&
                path == "/$AVATAR_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val username = request.uri.getQueryParameter(QUERY_USERNAME)
            ?: throw IllegalArgumentException("Nullable username")

        val response = apiClient.getAvatar(username)
        if (response.hasError()) {
            throw IOException("${response.apiError}")
        } else {
            return Result(Okio.source(response.stream), Picasso.LoadedFrom.NETWORK)
        }
    }
}
