package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes

/**
 * Integration test for [SubsonicAPIClient] for getArtists() request.
 */
class SubsonicApiGetArtistsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get artists error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getArtists(null).execute()
        }

        response.indexes `should not be` null
        response.indexes `should equal` Indexes()
    }

    @Test
    fun `Should parse get artists ok reponse`() {
        mockWebServerRule.enqueueResponse("get_artists_ok.json")

        val response = client.api.getArtists(null).execute()

        assertResponseSuccessful(response)
        with(response.body().indexes) {
            lastModified `should equal to` 0L
            ignoredArticles `should equal to` "The El La Los Las Le Les"
            shortcutList `should equal` emptyList()
            indexList.size `should equal to` 2
            indexList `should equal` listOf(
                    Index(name = "A", artists = listOf(
                            Artist(id = "362", name = "AC/DC", coverArt = "ar-362", albumCount = 2),
                            Artist(id = "254", name = "Acceptance", coverArt = "ar-254", albumCount = 1)
                    )),
                    Index(name = "T", artists = listOf(
                            Artist(id = "516", name = "Tangerine Dream", coverArt = "ar-516", albumCount = 1),
                            Artist(id = "242", name = "Taproot", coverArt = "ar-242", albumCount = 2)
                    ))
            )
        }
    }

    @Test
    fun `Should pass param on query for get artists call`() {
        mockWebServerRule.enqueueResponse("get_artists_ok.json")
        val musicFolderId = "101"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_artists_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.getArtists(musicFolderId).execute()
        }
    }
}
