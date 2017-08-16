package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getMusicDirectory request.
 */
class SubsonicApiGetMusicDirectoryTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse getMusicDirectory error response`() {
        val response = checkErrorCallParsed(mockWebServerRule, {
            client.api.getMusicDirectory(1).execute()
        })

        response.musicDirectory `should be` null
    }

    @Test
    fun `GetMusicDirectory should add directory id to query params`() {
        mockWebServerRule.enqueueResponse("get_music_directory_ok.json")
        val directoryId = 124L

        client.api.getMusicDirectory(directoryId).execute()

        mockWebServerRule.mockWebServer.takeRequest().requestLine `should contain` "id=$directoryId"
    }

    @Test
    fun `Should parse get music directory ok response`() {
        mockWebServerRule.enqueueResponse("get_music_directory_ok.json")

        val response = client.api.getMusicDirectory(1).execute()

        assertResponseSuccessful(response)

        response.body().musicDirectory `should not be` null
        with(response.body().musicDirectory!!) {
            id `should equal` 382L
            name `should equal` "AC_DC"
            starred `should equal` parseDate("2017-04-02T20:16:29.815Z")
            childList.size `should be` 2
            childList[0] `should equal` MusicDirectoryChild(583L, 382L, true, "Black Ice",
                    "Black Ice", "AC/DC", 2008, "Hard Rock", 583L,
                    parseDate("2016-10-23T15:31:22.000Z"), parseDate("2017-04-02T20:16:15.724Z"))
            childList[1] `should equal` MusicDirectoryChild(582L, 382L, true, "Rock or Bust",
                    "Rock or Bust", "AC/DC", 2014, "Hard Rock", 582L,
                    parseDate("2016-10-23T15:31:24.000Z"), null)
        }
    }
}
