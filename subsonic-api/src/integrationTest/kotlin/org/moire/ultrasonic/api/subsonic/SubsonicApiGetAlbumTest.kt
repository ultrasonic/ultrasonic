package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getAlbum call.
 */
class SubsonicApiGetAlbumTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error responce`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.getAlbum(56L).execute()
        })
    }

    @Test
    fun `Should add id to request params`() {
        mockWebServerRule.enqueueResponse("get_album_ok.json")
        val id = 76L
        client.api.getAlbum(id).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "id=$id"
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("get_album_ok.json")

        val response = client.api.getAlbum(512L).execute()

        assertResponseSuccessful(response)
        with(response.body().album) {
            id `should equal to` 618L
            name `should equal to` "Black Ice"
            artist `should equal to` "AC/DC"
            artistId `should equal to` 362L
            coverArt `should equal to` "al-618"
            songCount `should equal to` 15
            duration `should equal to` 3331
            created `should equal` parseDate("2016-10-23T15:31:22.000Z")
            year `should equal to` 2008
            genre `should equal to` "Hard Rock"
            songList.size `should equal to` 15
            songList[0] `should equal` MusicDirectoryChild(id = 6491L, parent = 6475L, isDir = false,
                    title = "Rock 'n' Roll Train", album = "Black Ice", artist = "AC/DC",
                    track = 1, year = 2008, genre = "Hard Rock", coverArt = "6475", size = 7205451,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 261, bitRate = 219,
                    path = "AC_DC/Black Ice/01 Rock 'n' Roll Train.mp3", isVideo = false,
                    playCount = 0, discNumber = 1, created = parseDate("2016-10-23T15:31:20.000Z"),
                    albumId = 618L, artistId = 362L, type = "music")
            songList[5] `should equal` MusicDirectoryChild(id = 6492L, parent = 6475L, isDir = false,
                    title = "Smash 'n' Grab", album = "Black Ice", artist = "AC/DC", track = 6,
                    year = 2008, genre = "Hard Rock", coverArt = "6475", size = 6697204,
                    contentType = "audio/mpeg", suffix = "mp3", duration = 246, bitRate = 216,
                    path = "AC_DC/Black Ice/06 Smash 'n' Grab.mp3", isVideo = false, playCount = 0,
                    discNumber = 1, created = parseDate("2016-10-23T15:31:20.000Z"),
                    albumId = 618L, artistId = 362L, type = "music")
        }
    }
}
