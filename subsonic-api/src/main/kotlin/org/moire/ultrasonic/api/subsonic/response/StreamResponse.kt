package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicError
import java.io.InputStream

/**
 * Special response that contains either [stream] of data from api, or [apiError],
 * or [responseHttpCode].
 *
 * [responseHttpCode] will be there always.
 */
class StreamResponse(val stream: InputStream? = null,
                     val apiError: SubsonicError? = null,
                     val responseHttpCode: Int) {
    /**
     * Check if this response has error.
     */
    fun hasError(): Boolean = apiError != null || responseHttpCode !in 200..300
}
