package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicError
import java.io.InputStream

/**
 * Special response that contains either [stream] of data from api, or [apiError],
 * or [requestErrorCode].
 *
 * [requestErrorCode] will be only if there problem on http level.
 */
class StreamResponse(val stream: InputStream? = null,
                     val apiError: SubsonicError? = null,
                     val requestErrorCode: Int? = null) {
    /**
     * Check if this response has error.
     */
    fun hasError(): Boolean = apiError != null || requestErrorCode != null
}
