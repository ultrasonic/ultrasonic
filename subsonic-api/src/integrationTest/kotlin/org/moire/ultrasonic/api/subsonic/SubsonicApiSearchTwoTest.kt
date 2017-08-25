package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for search2 call.
 */
class SubsonicApiSearchTwoTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.search2("some-query").execute()
        })
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("search2_ok.json")

        val response = client.api.search2("some-query").execute()

        assertResponseSuccessful(response)
        with(response.body().searchResult) {
            artistList.size `should equal to` 1
            artistList[0] `should equal` Artist(id = 522, name = "The Prodigy")
            albumList.size `should equal to` 1
            albumList[0] `should equal` MusicDirectoryChild(id = 8867, parent = 522, isDir = true,
                    title = "Always Outnumbered, Never Outgunned",
                    album = "Always Outnumbered, Never Outgunned", artist = "The Prodigy",
                    year = 2004, genre = "Electronic", coverArt = "8867", playCount = 0,
                    created = parseDate("2016-10-23T20:57:27.000Z"))
            songList.size `should equal to` 1
            songList[0] `should equal` MusicDirectoryChild(id = 5831, parent = 5766, isDir = false,
                    title = "You'll Be Under My Wheels", album = "Need for Speed Most Wanted",
                    artist = "The Prodigy", track = 17, year = 2005, genre = "Rap",
                    coverArt = "5766", size = 5607024, contentType = "audio/mpeg",
                    suffix = "mp3", duration = 233, bitRate = 192,
                    path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels.mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T20:09:02.000Z"),
                    albumId = 568, artistId = 505, type = "music")
        }
    }

    @Test
    fun `Should pass query id in request param`() {
        val query = "some"

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2(query).execute()
        }, expectedParam = "query=$query")
    }

    @Test
    fun `Should pass artist count in request param`() {
        val artistCount = 45

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", artistCount = artistCount).execute()
        }, expectedParam = "artistCount=$artistCount")
    }

    @Test
    fun `Should pass artist offset in request param`() {
        val artistOffset = 13

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", artistOffset = artistOffset).execute()
        }, expectedParam = "artistOffset=$artistOffset")
    }

    @Test
    fun `Should pass album count in request param`() {
        val albumCount = 30

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", albumCount = albumCount).execute()
        }, expectedParam = "albumCount=$albumCount")
    }

    @Test
    fun `Should pass album offset in request param`() {
        val albumOffset = 91

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", albumOffset = albumOffset).execute()
        }, expectedParam = "albumOffset=$albumOffset")
    }

    @Test
    fun `Should pass song count in request param`() {
        val songCount = 22

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", songCount = songCount).execute()
        }, expectedParam = "songCount=$songCount")
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = 565L

        mockWebServerRule.assertRequestParam(responseResourceName = "search2_ok.json", apiRequest = {
            client.api.search2("some", musicFolderId = musicFolderId).execute()
        }, expectedParam = "musicFolderId=$musicFolderId")
    }
}
