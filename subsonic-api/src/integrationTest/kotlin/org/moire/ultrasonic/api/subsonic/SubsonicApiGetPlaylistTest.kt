package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.Playlist

/**
 * Integration test for [SubsonicAPIClient] for getPlaylist call.
 */
class SubsonicApiGetPlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getPlaylist(10).execute()
        }

        response.playlist `should not be` null
        response.playlist `should equal` Playlist()
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_playlist_ok.json")

        val response = client.api.getPlaylist(4).execute()

        assertResponseSuccessful(response)
        with(response.body().playlist) {
            id `should equal to` 0
            name `should equal to` "Aug 27, 2017 11:17 AM"
            owner `should equal to` "admin"
            public `should equal to` false
            songCount `should equal to` 16
            duration `should equal to` 3573
            created `should equal` parseDate("2017-08-27T11:17:26.216Z")
            changed `should equal` parseDate("2017-08-27T11:17:26.218Z")
            coverArt `should equal to` "pl-0"
            entriesList.size `should equal to` 2
            entriesList[1] `should equal` MusicDirectoryChild(id = 4215, parent = 4186,
                    isDir = false, title = "Going to Hell", album = "Going to Hell",
                    artist = "The Pretty Reckless", track = 2, year = 2014,
                    genre = "Hard Rock", coverArt = "4186", size = 11089627,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 277, bitRate = 320,
                    path = "The Pretty Reckless/Going to Hell/02 Going to Hell.mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T21:30:41.000Z"),
                    albumId = 388, artistId = 238, type = "music")
        }
    }

    @Test
    fun `Should pass id as request param`() {
        val playlistId = 453L

        mockWebServerRule.assertRequestParam(responseResourceName = "get_playlist_ok.json",
                expectedParam = "id=$playlistId") {
            client.api.getPlaylist(playlistId).execute()
        }
    }
}
