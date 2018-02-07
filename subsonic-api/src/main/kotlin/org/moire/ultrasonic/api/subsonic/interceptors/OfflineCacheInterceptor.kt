package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response
import org.moire.ultrasonic.api.subsonic.NetworkStateIndicator
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_STALE_SECONDS = 60 * 60 * 24 * 30 * 3 // ~3 Months

/**
 * Special [Interceptor] that tries to load response from cache if device is offline.
 */
class OfflineCacheInterceptor(
        private val networkState: NetworkStateIndicator,
        private val maxStaleTimeSec: Int = DEFAULT_MAX_STALE_SECONDS
) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        return if (networkState.isOnline()) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().apply { applyOfflineCacheControl() }.build())
        }
    }

    private fun Request.Builder.applyOfflineCacheControl() {
        val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(maxStaleTimeSec, TimeUnit.SECONDS)
                .build()
        cacheControl(cacheControl)
    }
}

/**
 * Rewrites [Response] headers:
 * - Removes all original cache control headers
 * - adds following header - `Cache-Control: private, max-age=0`
 *
 * Only do rewrite for `Content-Type: application/json` responses.
 */
class EnableCachingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val response = chain.proceed(chain.request())
        return if (response.isSuccessful) {
            response.modifyCacheHeaders()
        } else {
            response
        }
    }

    private fun Response.modifyCacheHeaders(): Response {
        val headers = headers()
        val contentTypeHeader = headers["Content-Type"]
        return if (contentTypeHeader != null &&
                contentTypeHeader.contains("application/json", ignoreCase = true)) {
            newBuilder()
                    .removeHeader("Cache-Control")
                    .removeHeader("Pragma")
                    .addHeader("Cache-Control", "private, max-age=0")
                    .build()
        } else {
            this
        }
    }
}
