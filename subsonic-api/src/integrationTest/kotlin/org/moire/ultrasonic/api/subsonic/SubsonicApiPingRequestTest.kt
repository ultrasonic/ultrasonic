package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] that checks ping api call.
 */
class SubsonicApiPingRequestTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse ping ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.ping().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
        }
    }

    @Test
    fun `Should parse ping error response`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.ping().execute()
        })
    }
}
