package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
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
        response.body()?.status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse star error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.star().execute()
        }
    }

    @Test
    fun `Should pass id param`() {
        val id = "110"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.star(id = id).execute()
        }
    }

    @Test
    fun `Should pass artist id param`() {
        val artistId = "123"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "artistId=$artistId"
        ) {
            client.api.star(artistId = artistId).execute()
        }
    }

    @Test
    fun `Should pass album id param`() {
        val albumId = "1001"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "albumId=$albumId"
        ) {
            client.api.star(albumId = albumId).execute()
        }
    }
}
