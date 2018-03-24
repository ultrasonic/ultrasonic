package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.getShares] call.
 */
class SubsonicApiGetSharesTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getShares().execute()
        }

        response.shares `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_shares_ok.json")

        val response = client.api.getShares().execute()

        assertResponseSuccessful(response)
        response.body()!!.shares.size `should be equal to` 1
        with(response.body()!!.shares[0]) {
            id `should be equal to` "0"
            url `should be equal to` "https://subsonic.com/ext/share/awdwo?jwt=eyJhbGciOiJIUzI1" +
                    "NiJ9.eyJwYXRoIjoiL2V4dC9zaGFyZS9hd2R3byIsImV4cCI6MTU0MTYyNjQzMX0.iy8dkt_ZZc8" +
                    "hJ692UxorHdHWFU2RB-fMCmCA4IJ_dTw"
            username `should be equal to` "admin"
            created `should equal` parseDate("2017-11-07T21:33:51.748Z")
            expires `should equal` parseDate("2018-11-07T21:33:51.748Z")
            lastVisited `should equal` parseDate("2018-11-07T21:33:51.748Z")
            visitCount `should be equal to` 0
            description `should be equal to` "Awesome link!"
            items.size `should be equal to` 1
            items[0] `should equal` MusicDirectoryChild(id = "4212", parent = "4186", isDir = false,
                    title = "Heaven Knows", album = "Going to Hell", artist = "The Pretty Reckless",
                    track = 3, year = 2014, genre = "Hard Rock", coverArt = "4186", size = 9025090,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 225, bitRate = 320,
                    path = "The Pretty Reckless/Going to Hell/03 Heaven Knows.mp3", isVideo = false,
                    playCount = 2, discNumber = 1, created = parseDate("2016-10-23T21:30:40.000Z"),
                    albumId = "388", artistId = "238", type = "music")
        }
    }
}
