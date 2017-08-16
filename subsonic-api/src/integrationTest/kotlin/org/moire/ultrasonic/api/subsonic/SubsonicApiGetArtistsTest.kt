package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index

/**
 * Integration test for [SubsonicAPIClient] for getArtists() request.
 */
class SubsonicApiGetArtistsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get artists error response`() {
        val response = checkErrorCallParsed(mockWebServerRule, {
            client.api.getArtists(null).execute()
        })

        response.indexes `should not be` null
        with(response.indexes) {
            lastModified `should equal to` 0
            ignoredArticles `should equal to` ""
            indexList.size `should equal to` 0
            shortcutList.size `should equal to` 0
        }
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
                            Artist(id = 362L, name = "AC/DC", coverArt = "ar-362", albumCount = 2),
                            Artist(id = 254L, name = "Acceptance", coverArt = "ar-254", albumCount = 1)
                    )),
                    Index(name = "T", artists = listOf(
                            Artist(id = 516L, name = "Tangerine Dream", coverArt = "ar-516", albumCount = 1),
                            Artist(id = 242L, name = "Taproot", coverArt = "ar-242", albumCount = 2)
                    ))
            )
        }
    }

    @Test
    fun `Should pass param on query for get artists call`() {
        mockWebServerRule.enqueueResponse("get_artists_ok.json")
        val musicFolderId = 101L
        client.api.getArtists(musicFolderId).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "musicFolderId=$musicFolderId"
    }
}
