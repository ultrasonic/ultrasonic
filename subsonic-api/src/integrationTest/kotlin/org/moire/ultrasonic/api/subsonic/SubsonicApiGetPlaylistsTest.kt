package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Playlist

/**
 * Integration test for [SubsonicAPIClient] for getPlaylists call.
 */
class SubsonicApiGetPlaylistsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error call`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getPlaylists().execute()
        }

        response.playlists `should not be` null
        response.playlists `should equal` emptyList()
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_playlists_ok.json")

        val response = client.api.getPlaylists().execute()

        assertResponseSuccessful(response)
        with(response.body().playlists) {
            size `should equal to` 1
            this[0] `should equal` Playlist(id = "0", name = "Aug 27, 2017 11:17 AM",
                    owner = "admin", public = false, songCount = 16, duration = 3573,
                    comment = "Some comment",
                    created = parseDate("2017-08-27T11:17:26.216Z"),
                    changed = parseDate("2017-08-27T11:17:26.218Z"),
                    coverArt = "pl-0")
        }
    }

    @Test
    fun `Should pass username as a parameter`() {
        val username = "SomeUsername"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_playlists_ok.json",
                expectedParam = "username=$username") {
            client.api.getPlaylists(username = username).execute()
        }
    }
}
