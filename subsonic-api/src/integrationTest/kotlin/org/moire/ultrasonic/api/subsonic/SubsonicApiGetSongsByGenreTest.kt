package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.getSongsByGenre] call.
 */
class SubsonicApiGetSongsByGenreTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getSongsByGenre("Metal").execute()
        }

        response.songsList `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_songs_by_genre_ok.json")

        val response = client.api.getSongsByGenre("Trance").execute()

        assertResponseSuccessful(response)
        response.body().songsList.size `should equal to` 2
        with(response.body().songsList) {
            this[0] `should equal` MusicDirectoryChild(id = 575, parent = 576, isDir = false,
                    title = "Time Machine (Vadim Zhukov Remix)", album = "668",
                    artist = "Tasadi", year = 2008, genre = "Trance", size = 22467672,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 561, bitRate = 320,
                    path = "Tasadi/668/00 Time Machine (Vadim Zhukov Remix).mp3",
                    isVideo = false, playCount = 0, created = parseDate("2016-10-23T21:58:29.000Z"),
                    albumId = 0, artistId = 0, type = "music")
            this[1] `should equal` MusicDirectoryChild(id = 621, parent = 622, isDir = false,
                    title = "My Heart (Vadim Zhukov Remix)", album = "668",
                    artist = "DJ Polyakov PPK Feat Kate Cameron", year = 2009, genre = "Trance",
                    size = 26805932, contentType = "audio/mpeg", suffix = "mp3", duration = 670,
                    bitRate = 320,
                    path = "DJ Polyakov PPK Feat Kate Cameron/668/00 My Heart (Vadim Zhukov Remix).mp3",
                    isVideo = false, playCount = 2, created = parseDate("2016-10-23T21:58:29.000Z"),
                    albumId = 5, artistId = 4, type = "music")
        }
    }

    @Test
    fun `Should pass genre in request param`() {
        val genre = "Rock"
        mockWebServerRule.assertRequestParam(expectedParam = "genre=$genre") {
            client.api.getSongsByGenre(genre = genre).execute()
        }
    }

    @Test
    fun `Should pass count in request param`() {
        val count = 494

        mockWebServerRule.assertRequestParam(expectedParam = "count=$count") {
            client.api.getSongsByGenre("Trance", count = count).execute()
        }
    }

    @Test
    fun `Should pass offset in request param`() {
        val offset = 31

        mockWebServerRule.assertRequestParam(expectedParam = "offset=$offset") {
            client.api.getSongsByGenre("Trance", offset = offset).execute()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = 1010L

        mockWebServerRule.assertRequestParam(expectedParam = "musicFolderId=$musicFolderId") {
            client.api.getSongsByGenre("Trance", musicFolderId = musicFolderId).execute()
        }
    }
}
