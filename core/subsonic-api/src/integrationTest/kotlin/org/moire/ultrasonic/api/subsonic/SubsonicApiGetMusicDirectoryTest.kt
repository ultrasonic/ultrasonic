package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getMusicDirectory request.
 */
class SubsonicApiGetMusicDirectoryTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse getMusicDirectory error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getMusicDirectory("1").execute()
        }

        response.musicDirectory `should not be` null
        response.musicDirectory `should be equal to` MusicDirectory()
    }

    @Test
    fun `GetMusicDirectory should add directory id to query params`() {
        val directoryId = "124"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_music_directory_ok.json",
            expectedParam = "id=$directoryId"
        ) {
            client.api.getMusicDirectory(directoryId).execute()
        }
    }

    @Test
    fun `Should parse get music directory ok response`() {
        mockWebServerRule.enqueueResponse("get_music_directory_ok.json")

        val response = client.api.getMusicDirectory("1").execute()

        assertResponseSuccessful(response)

        response.body()!!.musicDirectory `should not be` null
        with(response.body()!!.musicDirectory) {
            id `should be equal to` "4836"
            parent `should be equal to` "300"
            name `should be equal to` "12 Stones"
            userRating `should be equal to` 5
            averageRating `should be equal to` 5.0f
            starred `should be equal to` null
            playCount `should be equal to` 1
            childList.size `should be` 2
            childList[0] `should be equal to` MusicDirectoryChild(
                id = "4844", parent = "4836",
                isDir = false, title = "Crash", album = "12 Stones", artist = "12 Stones",
                track = 1, year = 2002, genre = "Alternative Rock", coverArt = "4836",
                size = 5348318L, contentType = "audio/mpeg", suffix = "mp3", duration = 222,
                bitRate = 192, path = "12 Stones/12 Stones/01 Crash.mp3", isVideo = false,
                playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T15:19:10.000Z"),
                albumId = "454", artistId = "288", type = "music"
            )
            childList[1] `should be equal to` MusicDirectoryChild(
                id = "4845", parent = "4836",
                isDir = false, title = "Broken", album = "12 Stones", artist = "12 Stones",
                track = 2, year = 2002, genre = "Alternative Rock", coverArt = "4836",
                size = 4309043L, contentType = "audio/mpeg", suffix = "mp3", duration = 179,
                bitRate = 192, path = "12 Stones/12 Stones/02 Broken.mp3", isVideo = false,
                playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T15:19:09.000Z"),
                albumId = "454", artistId = "288", type = "music"
            )
        }
    }
}
