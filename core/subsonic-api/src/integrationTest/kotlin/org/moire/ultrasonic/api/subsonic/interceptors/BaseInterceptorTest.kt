package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Before
import org.junit.Rule
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule

/**
 * Base class for testing [okhttp3.Interceptor] implementations.
 */
abstract class BaseInterceptorTest {
    @Rule @JvmField val mockWebServerRule = MockWebServerRule()

    lateinit var client: OkHttpClient

    abstract val interceptor: Interceptor

    @Before
    fun setUp() {
        client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    /**
     * Creates [Request] to use with [mockWebServerRule].
     *
     * @param additionalParams passes [Request.Builder] to add additionally required
     * params to the [Request].
     */
    fun createRequest(additionalParams: (Request.Builder) -> Unit): Request = Request.Builder()
        .url(mockWebServerRule.mockWebServer.url("/"))
        .also { additionalParams(it) }
        .build()
}
