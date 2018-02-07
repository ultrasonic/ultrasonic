package org.moire.ultrasonic.api.subsonic.interceptors

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.moire.ultrasonic.api.subsonic.NetworkStateIndicator
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule

/**
 * Integration tests for [EnableCachingNetworkInterceptor].
 */
class EnableCachingNetworkInterceptorTest : BaseInterceptorTest() {
    @get:Rule val tempDirRule = TemporaryFolder()

    private val networkStateMock = mock<NetworkStateIndicator> {
        on { isOnline() } `it returns` true
    }

    override val interceptor: Interceptor
        get() = OfflineCacheInterceptor(networkStateMock, 1)

    private val rewriteInterceptor = EnableCachingNetworkInterceptor()
    private lateinit var cache: Cache
    private lateinit var clientWithCache: OkHttpClient

    @Before
    override fun setUp() {
        super.setUp()

        cache = Cache(tempDirRule.newFolder(), Long.MAX_VALUE)
        clientWithCache = client.newBuilder()
                .cache(cache)
                .addNetworkInterceptor(rewriteInterceptor)
                .build()
    }

    @Test
    fun `Should add cache control from json response`() {
        mockWebServerRule.enqueueResponse(body = "1")
        mockWebServerRule.enqueueResponse(body = "2")

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        whenever(networkStateMock.isOnline()).thenReturn(false)
        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.body()?.string() `should equal` "1"
    }

    @Test
    fun `Should not add cache control for non-json response`() {
        mockWebServerRule.enqueueResponse(body = "1", contentType = "octet/stream")
        mockWebServerRule.enqueueResponse(body = "2", contentType = "octet/stream")

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        whenever(networkStateMock.isOnline()).thenReturn(false)
        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` false
    }

    private fun MockWebServerRule.enqueueResponse(
            body: String = "42",
            contentType: String = "application/json",
            disableCache: Boolean = true
    ) {
        val response = MockResponse()
                .setHeader("Content-Type", contentType)
                .apply {
                    if (disableCache) {
                        setHeader("Cache-Control", "no-cache, no-store")
                        setHeader("Pragma", "no-cache")
                    }
                }
                .setResponseCode(200)
                .setBody(body)
        mockWebServer.enqueue(response)
    }
}