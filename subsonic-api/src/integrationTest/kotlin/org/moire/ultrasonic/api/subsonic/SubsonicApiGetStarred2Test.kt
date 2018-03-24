package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

/**
 * Integration test for [SubsonicAPIClient] for getStarred2 call.
 */
@Suppress("NamingConventionViolation")
class SubsonicApiGetStarred2Test : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getStarred2().execute()
        }

        response.starred2 `should equal` SearchTwoResult()
    }

    @Test
    fun `Should handle ok reponse`() {
        mockWebServerRule.enqueueResponse("get_starred_2_ok.json")

        val response = client.api.getStarred2().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.starred2) {
            albumList `should equal` emptyList()
            artistList.size `should equal to` 1
            artistList[0] `should equal` Artist(id = "364", name = "Parov Stelar",
                    starred = parseDate("2017-08-12T18:32:58.768Z"))
            songList `should equal` emptyList()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = "441"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_starred_2_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.getStarred2(musicFolderId = musicFolderId).execute()
        }
    }
}
