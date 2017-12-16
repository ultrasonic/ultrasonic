package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getRandomSongs call.
 */
class SubsonicApiGetRandomSongsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getRandomSongs().execute()
        }

        response.songsList `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_random_songs_ok.json")

        val response = client.api.getRandomSongs().execute()

        assertResponseSuccessful(response)
        with(response.body().songsList) {
            size `should equal to` 3
            this[1] `should equal` MusicDirectoryChild(id = "3061", parent = "3050", isDir = false,
                    title = "Sure as Hell", album = "Who Are You Now?", artist = "This Providence",
                    track = 1, year = 2009, genre = "Indie Rock", coverArt = "3050",
                    size = 1969692, contentType = "audio/mpeg", suffix = "mp3", duration = 110,
                    bitRate = 142, path = "This Providence/Who Are You Now_/01 Sure as Hell.mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T21:32:46.000Z"), albumId = "272",
                    artistId = "152", type = "music")
        }
    }

    @Test
    fun `Should pass size in request param`() {
        val size = 384433

        mockWebServerRule.assertRequestParam(responseResourceName = "get_random_songs_ok.json",
                expectedParam = "size=$size") {
            client.api.getRandomSongs(size = size).execute()
        }
    }

    @Test
    fun `Should pass genre in request param`() {
        val genre = "PostRock"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_random_songs_ok.json",
                expectedParam = "genre=$genre") {
            client.api.getRandomSongs(genre = genre).execute()
        }
    }

    @Test
    fun `Should pass from year in request param`() {
        val fromYear = 1919

        mockWebServerRule.assertRequestParam(responseResourceName = "get_random_songs_ok.json",
                expectedParam = "fromYear=$fromYear") {
            client.api.getRandomSongs(fromYear = fromYear).execute()
        }
    }

    @Test
    fun `Should pass to year in request params`() {
        val toYear = 2012

        mockWebServerRule.assertRequestParam(responseResourceName = "get_random_songs_ok.json",
                expectedParam = "toYear=$toYear") {
            client.api.getRandomSongs(toYear = toYear).execute()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = 4919L

        mockWebServerRule.assertRequestParam(responseResourceName = "get_random_songs_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.getRandomSongs(musicFolderId = musicFolderId).execute()
        }
    }
}
