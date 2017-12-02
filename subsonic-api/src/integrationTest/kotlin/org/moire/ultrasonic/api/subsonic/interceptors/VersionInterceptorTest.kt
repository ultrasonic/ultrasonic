package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.enqueueResponse
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Integration test for [VersionInterceptor].
 */
class VersionInterceptorTest : BaseInterceptorTest() {
    private val initialProtocolVersion = SubsonicAPIVersions.V1_1_0
    private var updatedProtocolVersion = SubsonicAPIVersions.V1_1_0

    override val interceptor: Interceptor by lazy(NONE) {
        VersionInterceptor(initialProtocolVersion) {
            updatedProtocolVersion = it
        }
    }

    @Test
    fun `Should add initial protocol version to request`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val request = createRequest {}

        client.newCall(request).execute()

        val requestLine = mockWebServerRule.mockWebServer.takeRequest().requestLine

        requestLine `should contain` "v=${initialProtocolVersion.restApiVersion}"
    }

    @Test
    fun `Should update version from response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.newCall(createRequest {}).execute()

        (interceptor as VersionInterceptor).protocolVersion `should equal` SubsonicAPIVersions.V1_13_0
    }

    @Test
    fun `Should not update version if response json doesn't contain version`() {
        mockWebServerRule.enqueueResponse("non_subsonic_response.json")

        client.newCall(createRequest {}).execute()

        (interceptor as VersionInterceptor).protocolVersion `should equal` initialProtocolVersion
    }

    @Test
    fun `Should not update version on non-json response`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse()
                .setBody("asdqwnekjnqwkjen")
                .setHeader("Content-Type", "application/octet-stream"))

        client.newCall(createRequest {}).execute()

        (interceptor as VersionInterceptor).protocolVersion `should equal` initialProtocolVersion
    }

    @Test
    fun `Should notify notifier on version change`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.newCall(createRequest {}).execute()

        updatedProtocolVersion `should equal` SubsonicAPIVersions.V1_13_0
    }
}
