package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Integration test for [SubsonicAPIClient] for unstar call.
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
        val id = "545"

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "id=$id") {
            client.api.unstar(id = id).execute()
        }
    }

    @Test
    fun `Should pass artistId param`() {
        val artistId = "644"

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "artistId=$artistId") {
            client.api.unstar(artistId = artistId).execute()
        }
    }

    @Test
    fun `Should pass albumId param`() {
        val albumId = "3344"

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "albumId=$albumId") {
            client.api.unstar(albumId = albumId).execute()
        }
    }
}
