package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor.Chain
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_12_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_13_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_16_0

/**
 * Unit test for [ProxyPasswordInterceptor].
 */
class ProxyPasswordInterceptorTest {
    private val mockPasswordHexInterceptor = mock<PasswordHexInterceptor>()
    private val mockPasswordMd5Interceptor = mock<PasswordMD5Interceptor>()
    private val mockChain = mock<Chain>()

    private val proxyInterceptor = ProxyPasswordInterceptor(
        V1_12_0,
        mockPasswordHexInterceptor, mockPasswordMd5Interceptor, false
    )

    @Test
    fun `Should use hex password on versions less then 1 13 0`() {
        proxyInterceptor.intercept(mockChain)

        verify(mockPasswordHexInterceptor).intercept(mockChain)
    }

    @Test
    fun `Should use md5 password on version 1 13 0`() {
        proxyInterceptor.apiVersion = V1_13_0

        proxyInterceptor.intercept(mockChain)

        verify(mockPasswordMd5Interceptor).intercept(mockChain)
    }

    @Test
    fun `Should use hex password if forceHex is true`() {
        val interceptor = ProxyPasswordInterceptor(
            V1_16_0, mockPasswordHexInterceptor,
            mockPasswordMd5Interceptor, true
        )

        interceptor.intercept(mockChain)

        verify(mockPasswordHexInterceptor).intercept(mockChain)
    }
}
