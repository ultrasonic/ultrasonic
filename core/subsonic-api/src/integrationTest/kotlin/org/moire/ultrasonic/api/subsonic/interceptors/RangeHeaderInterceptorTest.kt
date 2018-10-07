package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not contain`
import org.junit.Test

/**
 * Unit test for [RangeHeaderInterceptor].
 */
class RangeHeaderInterceptorTest : BaseInterceptorTest() {

    override val interceptor: Interceptor
        get() = RangeHeaderInterceptor()

    @Test
    fun `Should update uppercase range header`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse())
        val offset = 111
        val request = createRequest {
            it.addHeader("Range", "$offset")
        }

        client.newCall(request).execute()

        val executedRequest = mockWebServerRule.mockWebServer.takeRequest()
        executedRequest.headers.names() `should contain` "Range"
        executedRequest.headers["Range"]!! `should be equal to` "bytes=$offset-"
    }

    @Test
    fun `Should not add range header if request doesnt contain it`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse())
        val request = createRequest { }

        client.newCall(request).execute()

        val executedRequest = mockWebServerRule.mockWebServer.takeRequest()
        executedRequest.headers.names() `should not contain` "Range"
        executedRequest.headers.names() `should not contain` "range"
    }

    @Test
    fun `Should update lowercase range header`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse())
        val offset = 51233
        val request = createRequest {
            it.addHeader("range", "$offset")
        }

        client.newCall(request).execute()

        val executedRequest = mockWebServerRule.mockWebServer.takeRequest()
        executedRequest.headers.names() `should contain` "Range"
        executedRequest.headers["Range"]!! `should be equal to` "bytes=$offset-"
    }
}
