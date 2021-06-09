package org.moire.ultrasonic.imageloader

import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import java.io.IOException
import okio.Okio
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.toStreamResponse

/**
 * Loads avatars from subsonic api.
 */
class AvatarRequestHandler(
    private val apiClient: SubsonicAPIClient
) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME && path == "/$AVATAR_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val username = request.uri.getQueryParameter(QUERY_USERNAME)
            ?: throw IllegalArgumentException("Nullable username")

        val response = apiClient.api.getAvatar(username).execute().toStreamResponse()
        if (response.hasError() || response.stream == null) {
            throw IOException("${response.apiError}")
        } else {
            return Result(Okio.source(response.stream!!), Picasso.LoadedFrom.NETWORK)
        }
    }
}
