package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist

/**
 * Integration test for [SubsonicAPIClient] for getArtist call.
 */
class SubsonicApiGetArtistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error call`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getArtist("101").execute()
        }

        response.artist `should not be` null
        response.artist `should be equal to` Artist()
    }

    @Test
    fun `Should pass id param in request`() {
        val id = "929"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_artist_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.getArtist(id).execute()
        }
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_artist_ok.json")

        val response = client.api.getArtist("100").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.artist) {
            id `should be equal to` "362"
            name `should be equal to` "AC/DC"
            coverArt `should be equal to` "ar-362"
            albumCount `should be equal to` 2
            albumsList.size `should be equal to` 2
            albumsList[0] `should be equal to` Album(
                id = "618", name = "Black Ice", artist = "AC/DC",
                artistId = "362", coverArt = "al-618", songCount = 15, duration = 3331,
                created = parseDate("2016-10-23T15:31:22.000Z"),
                year = 2008, genre = "Hard Rock"
            )
            albumsList[1] `should be equal to` Album(
                id = "617", name = "Rock or Bust", artist = "AC/DC",
                artistId = "362", coverArt = "al-617", songCount = 11, duration = 2095,
                created = parseDate("2016-10-23T15:31:23.000Z"),
                year = 2014, genre = "Hard Rock"
            )
        }
    }
}
