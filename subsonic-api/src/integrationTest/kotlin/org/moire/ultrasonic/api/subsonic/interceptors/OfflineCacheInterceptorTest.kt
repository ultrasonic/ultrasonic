package org.moire.ultrasonic.api.subsonic.interceptors

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.moire.ultrasonic.api.subsonic.NetworkStateIndicator
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule
import java.util.concurrent.TimeUnit

/**
 * Integration test for [OfflineCacheInterceptor].
 */
class OfflineCacheInterceptorTest : BaseInterceptorTest() {
    @get:Rule val tempDirRule = TemporaryFolder()

    private val networkStateMock = mock<NetworkStateIndicator> {
        on { isOnline() } `it returns` true
    }

    override val interceptor: Interceptor
        get() = OfflineCacheInterceptor(networkStateMock, 1)

    private lateinit var cache: Cache
    private lateinit var clientWithCache: OkHttpClient

    @Before
    override fun setUp() {
        super.setUp()

        cache = Cache(tempDirRule.newFolder(), Long.MAX_VALUE)
        clientWithCache = client.newBuilder().cache(cache).build()
    }

    @Test
    fun `Should return from cache if cache-control is private when network is offline`() {
        mockWebServerRule.enqueueResponseWithCacheControl(body = "1", isPrivate = true)
        mockWebServerRule.enqueueResponseWithCacheControl(body = "2", isPrivate = true)

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        whenever(networkStateMock.isOnline()).thenReturn(false)
        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` true
        response2.body()?.string() `should equal` "1"
        response2.cacheResponse() `should not be` null
        response2.networkResponse() `should be` null
    }

    @Test
    fun `Should return from network if cache-control is private when network is online`() {
        mockWebServerRule.enqueueResponseWithCacheControl(body = "1", isPrivate = true)
        mockWebServerRule.enqueueResponseWithCacheControl(body = "2", isPrivate = true)

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` true
        response2.body()?.string() `should equal` "2"
        response2.cacheResponse() `should be` null
        response2.networkResponse() `should not be` null
    }

    @Test
    fun `Should return from from cache if cache-control is public when network is offline`() {
        mockWebServerRule.enqueueResponseWithCacheControl(body = "1", isPrivate = false)
        mockWebServerRule.enqueueResponseWithCacheControl(body = "2", isPrivate = false)

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        whenever(networkStateMock.isOnline()).thenReturn(false)
        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` true
        response2.body()?.string() `should equal` "1"
        response2.cacheResponse() `should not be` null
        response2.networkResponse() `should be` null
    }

    @Test
    fun `Should return from network if cache-control is public when network is online`() {
        mockWebServerRule.enqueueResponseWithCacheControl(body = "1", isPrivate = false)
        mockWebServerRule.enqueueResponseWithCacheControl(body = "2", isPrivate = false)

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` true
        response2.body()?.string() `should equal` "2"
        response2.cacheResponse() `should be` null
        response2.networkResponse() `should not be` null
    }

    @Test
    fun `Should fail call if max stale time exceeds`() {
        mockWebServerRule.enqueueResponseWithCacheControl(body = "1")
        mockWebServerRule.enqueueResponseWithCacheControl(body = "2")

        val response1 = clientWithCache.newCall(createRequest {}).execute()
        response1.body()?.string() `should equal` "1"

        Thread.sleep(1001)
        whenever(networkStateMock.isOnline()).thenReturn(false)
        val response2 = clientWithCache.newCall(createRequest {}).execute()

        response2.isSuccessful `should equal to` false
    }

    private fun MockWebServerRule.enqueueResponseWithCacheControl(
            body: String = "42",
            maxAge: Int = 0,
            isPrivate: Boolean = true
    ) {
        val privateHeaderValue = if (isPrivate) "private" else "public"
        val response = MockResponse()
                .addHeader("Cache-Control: $privateHeaderValue")
                .addHeader("Cache-Control: max-age=$maxAge")
                .setResponseCode(200)
                .setBody(body)
        mockWebServer.enqueue(response)
    }
}
