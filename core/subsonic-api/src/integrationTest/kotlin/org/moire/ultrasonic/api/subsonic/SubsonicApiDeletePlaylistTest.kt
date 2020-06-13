package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Instrumentation test for [SubsonicAPIClient] for deletePlaylist call.
 */
class SubsonicApiDeletePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.deletePlaylist("10").execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.deletePlaylist("10").execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id param in request`() {
        val id = "534"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.deletePlaylist(id).execute()
        }
    }
}
