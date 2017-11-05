package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

/**
 * Modifies request "Range" header to be according to HTTP standard.
 *
 * See [range rfc](https://tools.ietf.org/html/rfc7233).
 */
internal class RangeHeaderInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val originalRequest = chain.request()
        val headers = originalRequest.headers()
        return if (headers.names().contains("Range")) {
            val offset = "bytes=${headers["Range"]}-"
            chain.proceed(originalRequest.newBuilder()
                    .removeHeader("Range").addHeader("Range", offset)
                    .build())
        } else {
            chain.proceed(originalRequest)
        }
    }
}