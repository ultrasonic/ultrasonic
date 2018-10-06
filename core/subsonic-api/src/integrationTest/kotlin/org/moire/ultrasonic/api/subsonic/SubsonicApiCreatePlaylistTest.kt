package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for createPlaylist call.
 */
class SubsonicApiCreatePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.createPlaylist().execute()
        }
    }

    @Test
    fun `Should hanlde ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.createPlaylist().execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id param in request`() {
        val id = "56"

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "playlistId=$id") {
            client.api.createPlaylist(id = id).execute()
        }
    }

    @Test
    fun `Should pass name param in request`() {
        val name = "some-name"

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "name=$name") {
            client.api.createPlaylist(name = name).execute()
        }
    }

    @Test
    fun `Should pass song id param in request`() {
        val songId = listOf("4410", "852")

        mockWebServerRule.assertRequestParam(responseResourceName = "ping_ok.json",
                expectedParam = "songId=${songId[0]}&songId=${songId[1]}") {
            client.api.createPlaylist(songIds = songId).execute()
        }
    }
}
