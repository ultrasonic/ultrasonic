package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for updatePlaylist call.
 */
class SubsonicApiUpdatePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.updatePlaylist("10").execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.updatePlaylist("15").execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass playlist id param in request`() {
        val id = "5453"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "playlistId=$id"
        ) {
            client.api.updatePlaylist(id = id).execute()
        }
    }

    @Test
    fun `Should pass name param in request`() {
        val name = "some-name"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "name=$name"
        ) {
            client.api.updatePlaylist("22", name = name).execute()
        }
    }

    @Test
    fun `Should pass comment param in request`() {
        val comment = "some-unusual-comment"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "comment=$comment"
        ) {
            client.api.updatePlaylist("42", comment = comment).execute()
        }
    }

    @Test
    fun `Should pass public param in request`() {
        val public = true

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "public=$public"
        ) {
            client.api.updatePlaylist("53", public = public).execute()
        }
    }

    @Test
    fun `Should pass song ids to update in request`() {
        val songIds = listOf("45", "81")

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "songIdToAdd=${songIds[0]}&songIdToAdd=${songIds[1]}"
        ) {
            client.api.updatePlaylist("25", songIdsToAdd = songIds).execute()
        }
    }

    @Test
    fun `Should pass song indexes to remove in request`() {
        val songIndexesToRemove = listOf(129, 1)

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "songIndexToRemove=${songIndexesToRemove[0]}&" +
                "songIndexToRemove=${songIndexesToRemove[1]}"
        ) {
            client.api.updatePlaylist("49", songIndexesToRemove = songIndexesToRemove).execute()
        }
    }
}
