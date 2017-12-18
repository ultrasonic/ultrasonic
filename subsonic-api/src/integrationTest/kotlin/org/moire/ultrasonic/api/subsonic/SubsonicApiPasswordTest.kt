package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not contain`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] that checks proper user password handling.
 */
class SubsonicApiPasswordTest : SubsonicAPIClientTest() {
    @Test
    fun `Should pass PasswordMD5Interceptor in query params for api version 1 13 0`() {
        val clientV12 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_14_0, CLIENT_ID)
        mockWebServerRule.enqueueResponse("ping_ok.json")

        clientV12.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "&s="
            requestLine `should contain` "&t="
            requestLine `should not contain` "&p=enc:"
        }
    }

    @Test
    fun `Should pass PasswordHexInterceptor in query params for api version 1 12 0`() {
        val clientV11 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_12_0, CLIENT_ID)
        mockWebServerRule.enqueueResponse("ping_ok.json")

        clientV11.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should not contain` "&s="
            requestLine `should not contain` "&t="
            requestLine `should contain` "&p=enc:"
        }
    }
}
