package org.moire.ultrasonic.api.subsonic.interceptors

import kotlin.LazyThreadSafetyMode.NONE
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.enqueueResponse

/**
 * Integration test for [VersionInterceptor].
 */
class VersionInterceptorTest : BaseInterceptorTest() {
    private val initialProtocolVersion = SubsonicAPIVersions.V1_1_0

    override val interceptor: Interceptor by lazy(NONE) {
        VersionInterceptor(initialProtocolVersion)
    }

    @Test
    fun `Should add initial protocol version to request`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val request = createRequest {}

        client.newCall(request).execute()

        val requestLine = mockWebServerRule.mockWebServer.takeRequest().requestLine

        requestLine `should contain` "v=${initialProtocolVersion.restApiVersion}"
    }
}
