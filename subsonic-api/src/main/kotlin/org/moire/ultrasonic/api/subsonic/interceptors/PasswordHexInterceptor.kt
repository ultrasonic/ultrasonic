package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Adds password param converted to hex string in request url.
 *
 * Should enabled for request that runs again [org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_12_0]
 * or lower.
 */
class PasswordHexInterceptor(private val password: String) : Interceptor {
    private val passwordHex: String by lazy(NONE) {
        "enc:${password.toHexBytes()}"
    }

    override fun intercept(chain: Chain): Response {
        val originalRequest = chain.request()
        val updatedUrl = originalRequest.url().newBuilder()
                .addQueryParameter("p", passwordHex).build()
        return chain.proceed(originalRequest.newBuilder().url(updatedUrl).build())
    }
}
