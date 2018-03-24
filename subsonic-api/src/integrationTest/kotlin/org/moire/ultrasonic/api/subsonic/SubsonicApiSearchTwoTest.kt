package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

/**
 * Integration test for [SubsonicAPIClient] for search2 call.
 */
class SubsonicApiSearchTwoTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.search2("some-query").execute()
        }

        response.searchResult `should not be` null
        response.searchResult `should equal` SearchTwoResult()
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("search2_ok.json")

        val response = client.api.search2("some-query").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.searchResult) {
            artistList.size `should be equal to` 1
            artistList[0] `should equal` Artist(id = "522", name = "The Prodigy")
            albumList.size `should be equal to` 1
            albumList[0] `should equal` MusicDirectoryChild(id = "8867", parent = "522",
                    isDir = true, title = "Always Outnumbered, Never Outgunned",
                    album = "Always Outnumbered, Never Outgunned", artist = "The Prodigy",
                    year = 2004, genre = "Electronic", coverArt = "8867", playCount = 0,
                    created = parseDate("2016-10-23T20:57:27.000Z"))
            songList.size `should be equal to` 1
            songList[0] `should equal` MusicDirectoryChild(id = "5831", parent = "5766",
                    isDir = false,
                    title = "You'll Be Under My Wheels", album = "Need for Speed Most Wanted",
                    artist = "The Prodigy", track = 17, year = 2005, genre = "Rap",
                    coverArt = "5766", size = 5607024, contentType = "audio/mpeg",
                    suffix = "mp3", duration = 233, bitRate = 192,
                    path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels" +
                            ".mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T20:09:02.000Z"),
                    albumId = "568", artistId = "505", type = "music")
        }
    }

    @Test
    fun `Should pass query id in request param`() {
        val query = "some"

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "query=$query") {
            client.api.search2(query).execute()
        }
    }

    @Test
    fun `Should pass artist count in request param`() {
        val artistCount = 45

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "artistCount=$artistCount") {
            client.api.search2("some", artistCount = artistCount).execute()
        }
    }

    @Test
    fun `Should pass artist offset in request param`() {
        val artistOffset = 13

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "artistOffset=$artistOffset") {
            client.api.search2("some", artistOffset = artistOffset).execute()
        }
    }

    @Test
    fun `Should pass album count in request param`() {
        val albumCount = 30

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "albumCount=$albumCount") {
            client.api.search2("some", albumCount = albumCount).execute()
        }
    }

    @Test
    fun `Should pass album offset in request param`() {
        val albumOffset = 91

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "albumOffset=$albumOffset") {
            client.api.search2("some", albumOffset = albumOffset).execute()
        }
    }

    @Test
    fun `Should pass song count in request param`() {
        val songCount = 22

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "songCount=$songCount") {
            client.api.search2("some", songCount = songCount).execute()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = "565"

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.search2("some", musicFolderId = musicFolderId).execute()
        }
    }
}
