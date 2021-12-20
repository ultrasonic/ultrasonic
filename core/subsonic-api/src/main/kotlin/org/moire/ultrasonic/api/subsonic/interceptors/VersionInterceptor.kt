package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions

/**
 * Special [Interceptor] that adds client supported version to request
 * @author Yahor Berdnikau
 */
internal class VersionInterceptor(
    internal var protocolVersion: SubsonicAPIVersions
) : Interceptor {

    override fun intercept(chain: Chain): okhttp3.Response {
        val originalRequest = chain.request()

        val newRequest = originalRequest.newBuilder()
            .url(
                originalRequest
                    .url
                    .newBuilder()
                    .addQueryParameter("v", protocolVersion.restApiVersion)
                    .build()
            )
            .build()

        return chain.proceed(newRequest)
    }
}
