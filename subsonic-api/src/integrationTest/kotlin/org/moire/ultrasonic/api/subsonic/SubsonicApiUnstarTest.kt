package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Intergration test for [SubsonicAPIClient] for unstar call.
 */
class SubsonicApiUnstarTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.unstar().execute()

        assertResponseSuccessful(response)
        response.body().status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse error response`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.unstar().execute()
        })
    }

    @Test
    fun `Should pass id param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val id = 545L
        client.api.unstar(id = id).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "id=$id"
    }

    @Test
    fun `Should pass artistId param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val artistId = 644L
        client.api.unstar(artistId = artistId).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "artistId=$artistId"
    }

    @Test
    fun `Should pass albumId param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val albumId = 3344L
        client.api.unstar(albumId = albumId).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "albumId=$albumId"
    }
}
