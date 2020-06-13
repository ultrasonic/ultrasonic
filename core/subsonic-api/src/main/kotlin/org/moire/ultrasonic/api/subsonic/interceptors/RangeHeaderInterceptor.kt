package org.moire.ultrasonic.api.subsonic.interceptors

import java.util.concurrent.TimeUnit.MILLISECONDS
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

internal const val SOCKET_READ_TIMEOUT_DOWNLOAD = 30 * 1000
// Allow 20 seconds extra timeout pear MB offset.
internal const val TIMEOUT_MILLIS_PER_OFFSET_BYTE = 0.02

/**
 * Modifies request "Range" header to be according to HTTP standard.
 *
 * Also increases read timeout to allow server to transcode response and offset it.
 *
 * See [range rfc](https://tools.ietf.org/html/rfc7233).
 */
internal class RangeHeaderInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val originalRequest = chain.request()
        val headers = originalRequest.headers()
        return if (headers.names().contains("Range")) {
            val offsetValue = headers["Range"] ?: "0"
            val offset = "bytes=$offsetValue-"
            chain.withReadTimeout(getReadTimeout(offsetValue.toInt()), MILLISECONDS)
                .proceed(
                    originalRequest.newBuilder()
                        .removeHeader("Range").addHeader("Range", offset)
                        .build()
                )
        } else {
            chain.proceed(originalRequest)
        }
    }

    // Set socket read timeout. Note: The timeout increases as the offset gets larger. This is
    // to avoid the thrashing effect seen when offset is combined with transcoding/downsampling
    // on the server. In that case, the server uses a long time before sending any data,
    // causing the client to time out.
    private fun getReadTimeout(offset: Int) =
        (SOCKET_READ_TIMEOUT_DOWNLOAD + offset * TIMEOUT_MILLIS_PER_OFFSET_BYTE).toInt()
}
