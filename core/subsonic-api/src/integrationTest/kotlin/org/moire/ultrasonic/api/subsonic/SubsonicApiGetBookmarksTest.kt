package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.getBookmarks] call.
 */
class SubsonicApiGetBookmarksTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getBookmarks().execute()
        }

        response.bookmarkList `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_bookmarks_ok.json")

        val response = client.api.getBookmarks().execute()

        assertResponseSuccessful(response)
        response.body()!!.bookmarkList.size `should be equal to` 1
        with(response.body()!!.bookmarkList[0]) {
            position `should be equal to` 107914
            username `should be equal to` "CaptainEurope"
            comment `should be equal to` "Look at this"
            created `should be equal to` parseDate("2017-11-18T15:22:22.144Z")
            changed `should be equal to` parseDate("2017-11-18T15:22:22.144Z")
            entry `should be equal to` MusicDirectoryChild(
                id = "10349", parent = "10342",
                isDir = false, title = "Amerika", album = "Home of the Strange",
                artist = "Young the Giant", track = 1, year = 2016, genre = "Indie Rock",
                coverArt = "10342", size = 9628673, contentType = "audio/mpeg",
                suffix = "mp3", duration = 240, bitRate = 320,
                path = "Young the Giant/Home of the Strange/01 Amerika.mp3",
                isVideo = false, playCount = 2, discNumber = 1,
                created = parseDate("2017-11-01T17:46:52.000Z"),
                albumId = "984", artistId = "571", type = "music"
            )
        }
    }
}
