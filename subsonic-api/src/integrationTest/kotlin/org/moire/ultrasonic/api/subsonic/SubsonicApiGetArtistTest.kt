package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
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
            client.api.getArtist(101L).execute()
        }

        response.artist `should not be` null
        response.artist `should equal` Artist()
    }

    @Test
    fun `Should pass id param in request`() {
        val id = 929L

        mockWebServerRule.assertRequestParam(responseResourceName = "get_artist_ok.json",
                expectedParam = "id=$id") {
            client.api.getArtist(id).execute()
        }
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_artist_ok.json")

        val response = client.api.getArtist(100L).execute()

        assertResponseSuccessful(response)
        with(response.body().artist) {
            id `should equal to` "362"
            name `should equal to` "AC/DC"
            coverArt `should equal to` "ar-362"
            albumCount `should equal to` 2
            albumsList.size `should equal to` 2
            albumsList[0] `should equal` Album(id = 618L, name = "Black Ice", artist = "AC/DC",
                    artistId = 362L, coverArt = "al-618", songCount = 15, duration = 3331,
                    created = parseDate("2016-10-23T15:31:22.000Z"),
                    year = 2008, genre = "Hard Rock")
            albumsList[1] `should equal` Album(id = 617L, name = "Rock or Bust", artist = "AC/DC",
                    artistId = 362L, coverArt = "al-617", songCount = 11, duration = 2095,
                    created = parseDate("2016-10-23T15:31:23.000Z"),
                    year = 2014, genre = "Hard Rock")
        }
    }
}
