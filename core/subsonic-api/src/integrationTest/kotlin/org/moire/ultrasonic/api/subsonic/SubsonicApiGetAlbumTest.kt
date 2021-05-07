package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getAlbum call.
 */
class SubsonicApiGetAlbumTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error responce`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getAlbum("56").execute()
        }

        response.album `should not be` null
        response.album `should be equal to` Album()
    }

    @Test
    fun `Should add id to request params`() {
        val id = "76"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.getAlbum(id).execute()
        }
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_album_ok.json")

        val response = client.api.getAlbum("512").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.album) {
            id `should be equal to` "618"
            name `should be equal to` "Black Ice"
            artist `should be equal to` "AC/DC"
            artistId `should be equal to` "362"
            coverArt `should be equal to` "al-618"
            songCount `should be equal to` 15
            duration `should be equal to` 3331
            created `should be equal to` parseDate("2016-10-23T15:31:22.000Z")
            year `should be equal to` 2008
            genre `should be equal to` "Hard Rock"
            songList.size `should be equal to` 15
            songList[0] `should be equal to` MusicDirectoryChild(
                id = "6491", parent = "6475",
                isDir = false, title = "Rock 'n' Roll Train", album = "Black Ice",
                artist = "AC/DC", track = 1, year = 2008, genre = "Hard Rock",
                coverArt = "6475", size = 7205451, contentType = "audio/mpeg", suffix = "mp3",
                duration = 261, bitRate = 219,
                path = "AC_DC/Black Ice/01 Rock 'n' Roll Train.mp3",
                isVideo = false, playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T15:31:20.000Z"),
                albumId = "618", artistId = "362", type = "music"
            )
            songList[5] `should be equal to` MusicDirectoryChild(
                id = "6492", parent = "6475",
                isDir = false, title = "Smash 'n' Grab", album = "Black Ice", artist = "AC/DC",
                track = 6, year = 2008, genre = "Hard Rock", coverArt = "6475", size = 6697204,
                contentType = "audio/mpeg", suffix = "mp3", duration = 246, bitRate = 216,
                path = "AC_DC/Black Ice/06 Smash 'n' Grab.mp3", isVideo = false, playCount = 0,
                discNumber = 1, created = parseDate("2016-10-23T15:31:20.000Z"),
                albumId = "618", artistId = "362", type = "music"
            )
        }
    }
}
