package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions

/**
 * Proxy [Interceptor] that uses one of [hexInterceptor] or [mD5Interceptor] depends on [apiVersion].
 *
 * To force [hexInterceptor] set [forceHexPassword] to `true`. Usually it should be done only for
 * ldap users.
 */
internal class ProxyPasswordInterceptor(
        initialAPIVersions: SubsonicAPIVersions,
        private val hexInterceptor: PasswordHexInterceptor,
        private val mD5Interceptor: PasswordMD5Interceptor,
        private val forceHexPassword: Boolean = false
) : Interceptor {
    var apiVersion: SubsonicAPIVersions = initialAPIVersions

    override fun intercept(chain: Chain): Response =
            if (apiVersion < SubsonicAPIVersions.V1_13_0 ||
                    forceHexPassword) {
                hexInterceptor.intercept(chain)
            } else {
                mD5Interceptor.intercept(chain)
            }
}
