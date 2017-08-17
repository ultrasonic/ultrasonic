package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Integration test for [SubsonicAPIClient] for star request.
 */
class SubsonicApiStarTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse star ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.star().execute()

        assertResponseSuccessful(response)
        response.body().status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse star error response`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.star().execute()
        })
    }

    @Test
    fun `Should pass id param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val id = 110L
        client.api.star(id = id).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "id=$id"
    }

    @Test
    fun `Should pass artist id param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val artistId = 123L
        client.api.star(artistId = artistId).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "artistId=$artistId"
    }

    @Test
    fun `Should pass albom id param`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")
        val albumId = 1001L
        client.api.star(albumId = albumId).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "albumId=$albumId"
    }
}
