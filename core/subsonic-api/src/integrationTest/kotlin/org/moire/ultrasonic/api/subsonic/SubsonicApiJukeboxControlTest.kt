package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction.GET
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction.STATUS
import org.moire.ultrasonic.api.subsonic.models.JukeboxStatus
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.jukeboxControl] call.
 */
class SubsonicApiJukeboxControlTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.jukeboxControl(GET).execute()
        }

        response.jukebox `should equal` JukeboxStatus()
    }

    @Test
    fun `Should handle ok response with jukebox status`() {
        mockWebServerRule.enqueueResponse("jukebox_control_status_ok.json")

        val response = client.api.jukeboxControl(STATUS).execute()

        assertResponseSuccessful(response)
        with(response.body()!!.jukebox) {
            currentIndex `should be equal to` 94
            playing `should be equal to` true
            gain `should be equal to` 0.32f
            position `should be equal to` 3
            playlistEntries `should equal` emptyList()
        }
    }

    @Test
    fun `Should handle ok response with jukebox playlist`() {
        mockWebServerRule.enqueueResponse("jukebox_control_playlist_ok.json")

        val response = client.api.jukeboxControl(GET).execute()

        assertResponseSuccessful(response)
        with(response.body()!!.jukebox) {
            currentIndex `should be equal to` 887
            playing `should be equal to` false
            gain `should be equal to` 0.88f
            position `should be equal to` 2
            playlistEntries.size `should be equal to` 2
            playlistEntries[1] `should equal` MusicDirectoryChild(
                id = "4215", parent = "4186",
                isDir = false, title = "Going to Hell", album = "Going to Hell",
                artist = "The Pretty Reckless", track = 2, year = 2014, genre = "Hard Rock",
                coverArt = "4186", size = 11089627, contentType = "audio/mpeg",
                suffix = "mp3", duration = 277, bitRate = 320,
                path = "The Pretty Reckless/Going to Hell/02 Going to Hell.mp3",
                isVideo = false, playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T21:30:41.000Z"), albumId = "388",
                artistId = "238", type = "music"
            )
        }
    }

    @Test
    fun `Should pass action in request params`() {
        val action = JukeboxAction.SET_GAIN

        mockWebServerRule.assertRequestParam(expectedParam = "action=$action") {
            client.api.jukeboxControl(action).execute()
        }
    }

    @Test
    fun `Should pass index in request params`() {
        val index = 440

        mockWebServerRule.assertRequestParam(expectedParam = "index=$index") {
            client.api.jukeboxControl(GET, index = index).execute()
        }
    }

    @Test
    fun `Should pass offset in request params`() {
        val offset = 58223

        mockWebServerRule.assertRequestParam(expectedParam = "offset=$offset") {
            client.api.jukeboxControl(GET, offset = offset).execute()
        }
    }

    @Test
    fun `Should pass ids in request params`() {
        val id = listOf("some-id1", "some-id2")

        mockWebServerRule.assertRequestParam(expectedParam = "id=${id[0]}&id=${id[1]}") {
            client.api.jukeboxControl(GET, ids = id).execute()
        }
    }

    @Test
    fun `Should pass gain in request params`() {
        val gain = 0.73f

        mockWebServerRule.assertRequestParam(expectedParam = "gain=$gain") {
            client.api.jukeboxControl(GET, gain = gain).execute()
        }
    }
}
