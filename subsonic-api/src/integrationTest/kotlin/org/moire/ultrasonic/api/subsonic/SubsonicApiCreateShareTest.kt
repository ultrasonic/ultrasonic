package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import java.util.Calendar

/**
 * Instrumentation test for [SubsonicAPIDefinition.createShare] call.
 */
class SubsonicApiCreateShareTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error responce`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.createShare(listOf("some-id")).execute()
        }

        response.shares `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_shares_ok.json")

        val response = client.api.createShare(listOf("some-id")).execute()

        assertResponseSuccessful(response)
        response.body().shares.size `should equal to` 1
        with(response.body().shares[0]) {
            id `should equal to` 0
            url `should equal to` "https://subsonic.com/ext/share/awdwo?jwt=eyJhbGciOiJIUzI1NiJ9." +
                    "eyJwYXRoIjoiL2V4dC9zaGFyZS9hd2R3byIsImV4cCI6MTU0MTYyNjQzMX0.iy8dkt_ZZc8hJ692" +
                    "UxorHdHWFU2RB-fMCmCA4IJ_dTw"
            username `should equal to` "admin"
            created `should equal` parseDate("2017-11-07T21:33:51.748Z")
            expires `should equal` parseDate("2018-11-07T21:33:51.748Z")
            lastVisited `should equal` parseDate("2018-11-07T21:33:51.748Z")
            description `should equal to` "Awesome link!"
            visitCount `should equal to` 0
            items.size `should equal to` 1
            items[0] `should equal` MusicDirectoryChild(id = 4212, parent = 4186, isDir = false,
                    title = "Heaven Knows", album = "Going to Hell", artist = "The Pretty Reckless",
                    track = 3, year = 2014, genre = "Hard Rock", coverArt = "4186", size = 9025090,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 225, bitRate = 320,
                    path = "The Pretty Reckless/Going to Hell/03 Heaven Knows.mp3", isVideo = false,
                    playCount = 2, discNumber = 1, created = parseDate("2016-10-23T21:30:40.000Z"),
                    albumId = 388, artistId = 238, type = "music")
        }
    }

    @Test
    fun `Should pass ids in request param`() {
        val idsList = listOf("some-id1", "some-id2")
        mockWebServerRule.assertRequestParam(expectedParam = "id=${idsList[0]}&id=${idsList[1]}") {
            client.api.createShare(idsList).execute()
        }
    }

    @Test
    fun `Should pass description in request param`() {
        val description = "description-banana"

        mockWebServerRule.assertRequestParam(expectedParam = "description=$description") {
            client.api.createShare(idsToShare = listOf("id1", "id2"), description = description)
                    .execute()
        }
    }

    @Test
    fun `Should pass expires in request param`() {
        val expires = Calendar.getInstance().timeInMillis

        mockWebServerRule.assertRequestParam(expectedParam = "expires=$expires") {
            client.api.createShare(idsToShare = listOf("id1"), expires = expires).execute()
        }
    }
}
