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
        private val maxStaleTime: Int = DEFAULT_MAX_STALE_SECONDS
) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        if (networkState.isOnline()) {
            return chain.proceed(request)
        } else {
            return chain.proceed(request.newBuilder().apply { applyOfflineCacheControl() }.build())
        }
    }

    private fun Request.Builder.applyOfflineCacheControl() {
        val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(maxStaleTime, TimeUnit.SECONDS)
                .build()
        cacheControl(cacheControl)
    }
}
