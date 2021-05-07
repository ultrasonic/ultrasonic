package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

/**
 * Integration test for [SubsonicAPIClient] for getStarred call.
 */
class SubsonicApiGetStarredTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getStarred().execute()
        }

        response.starred `should be equal to` SearchTwoResult()
    }

    @Test
    fun `Should handle ok reponse`() {
        mockWebServerRule.enqueueResponse("get_starred_ok.json")

        val response = client.api.getStarred().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.starred) {
            albumList `should be equal to` emptyList()
            artistList.size `should be equal to` 1
            artistList[0] `should be equal to` Artist(
                id = "364", name = "Parov Stelar",
                starred = parseDate("2017-08-12T18:32:58.768Z")
            )
            songList `should be equal to` emptyList()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = "441"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_starred_ok.json",
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.getStarred(musicFolderId = musicFolderId).execute()
        }
    }
}
