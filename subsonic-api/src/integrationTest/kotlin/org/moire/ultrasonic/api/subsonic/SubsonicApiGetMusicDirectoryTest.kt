package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
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

        with(response.musicDirectory) {
            this `should not be` null
            id `should equal to` -1L
            parent `should equal to` -1L
            name `should equal to` ""
            userRating `should equal to` 0
            averageRating `should equal to` 0.0f
            starred `should be` null
            playCount `should equal to` 0
            childList `should be` emptyList<MusicDirectoryChild>()
        }
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
        with(response.body().musicDirectory) {
            id `should equal to` 4836L
            parent `should equal to` 300L
            name `should equal` "12 Stones"
            userRating `should equal to` 5
            averageRating `should equal to` 5.0f
            starred `should equal` null
            playCount `should equal to` 1
            childList.size `should be` 2
            childList[0] `should equal` MusicDirectoryChild(id = 4844L, parent = 4836L, isDir = false,
                    title = "Crash", album = "12 Stones", artist = "12 Stones", track = 1, year = 2002,
                    genre = "Alternative Rock", coverArt = 4836L, size = 5348318L,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 222, bitRate = 192,
                    path = "12 Stones/12 Stones/01 Crash.mp3", isVideo = false, playCount = 0,
                    discNumber = 1, created = parseDate("2016-10-23T15:19:10.000Z"),
                    albumId = 454L, artistId = 288L, type = "music")
            childList[1] `should equal` MusicDirectoryChild(id = 4845L, parent = 4836L, isDir = false,
                    title = "Broken", album = "12 Stones", artist = "12 Stones", track = 2, year = 2002,
                    genre = "Alternative Rock", coverArt = 4836L, size = 4309043L,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 179, bitRate = 192,
                    path = "12 Stones/12 Stones/02 Broken.mp3", isVideo = false, playCount = 0,
                    discNumber = 1, created = parseDate("2016-10-23T15:19:09.000Z"),
                    albumId = 454L, artistId = 288L, type = "music")
        }
    }
}
