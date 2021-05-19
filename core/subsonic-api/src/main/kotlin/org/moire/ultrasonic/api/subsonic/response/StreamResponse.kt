package org.moire.ultrasonic.api.subsonic.response

import java.io.InputStream
import org.moire.ultrasonic.api.subsonic.SubsonicError

/**
 * Special response that contains either [stream] of data from api, or [apiError],
 * or [responseHttpCode].
 *
 * [responseHttpCode] will be there always.
 */
@Suppress("MagicNumber")
class StreamResponse(
    val stream: InputStream? = null,
    val apiError: SubsonicError? = null,
    val responseHttpCode: Int
) {
    /**
     * Check if this response has error.
     */
    fun hasError(): Boolean = apiError != null || responseHttpCode !in 200..300
}
