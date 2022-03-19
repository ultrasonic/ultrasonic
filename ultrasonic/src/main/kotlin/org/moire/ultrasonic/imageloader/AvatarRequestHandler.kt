package org.moire.ultrasonic.imageloader

import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import java.io.IOException
import okio.source
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient

/**
 * Loads avatars from subsonic api.
 */
class AvatarRequestHandler(
    private val client: SubsonicAPIClient
) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return with(data.uri) {
            scheme == SCHEME && path == "/$AVATAR_PATH"
        }
    }

    override fun load(request: Request, networkPolicy: Int): Result {
        val username = request.uri.getQueryParameter(QUERY_USERNAME)
            ?: throw IllegalArgumentException("Nullable username")

        // Inverted call order, because Mockito has problems with chained calls.
        val response = client.toStreamResponse(client.api.getAvatar(username).execute())

        if (response.hasError() || response.stream == null) {
            throw IOException("${response.apiError}")
        } else {
            return Result(response.stream!!.source(), Picasso.LoadedFrom.NETWORK)
        }
    }
}
