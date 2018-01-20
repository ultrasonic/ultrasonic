package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchThreeResult

/**
 * Integration test for [SubsonicAPIClient] for search3 call.
 */
class SubsonicApiSearchThreeTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.search3("some-query").execute()
        }

        response.searchResult `should not be` null
        response.searchResult `should equal` SearchThreeResult()
    }

    @Test
    fun `Should parse ok response`() {
        mockWebServerRule.enqueueResponse("search3_ok.json")

        val response = client.api.search3("some-query").execute()

        assertResponseSuccessful(response)
        with(response.body().searchResult) {
            artistList.size `should equal to` 1
            artistList[0] `should equal` Artist(id = "505", name = "The Prodigy",
                    coverArt = "ar-505", albumCount = 5)
            albumList.size `should equal to` 1
            albumList[0] `should equal` Album(id = "855",
                    name = "Always Outnumbered, Never Outgunned",
                    artist = "The Prodigy", artistId = "505", coverArt = "al-855", songCount = 12,
                    duration = 3313, created = parseDate("2016-10-23T20:57:27.000Z"),
                    year = 2004, genre = "Electronic")
            songList.size `should equal to` 1
            songList[0] `should equal` MusicDirectoryChild(id = "5831", parent = "5766",
                    isDir = false,
                    title = "You'll Be Under My Wheels", album = "Need for Speed Most Wanted",
                    artist = "The Prodigy", track = 17, year = 2005, genre = "Rap",
                    coverArt = "5766", size = 5607024, contentType = "audio/mpeg",
                    suffix = "mp3", duration = 233, bitRate = 192,
                    path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels" +
                            ".mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T20:09:02.000Z"), albumId = "568",
                    artistId = "505", type = "music")
        }
    }

    @Test
    fun `Should pass query as request param`() {
        val query = "some-wip-query"

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "query=$query") {
            client.api.search3(query = query).execute()
        }
    }

    @Test
    fun `Should pass artist count as request param`() {
        val artistCount = 67

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "artistCount=$artistCount") {
            client.api.search3("some", artistCount = artistCount).execute()
        }
    }

    @Test
    fun `Should pass artist offset as request param`() {
        val artistOffset = 34

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "artistOffset=$artistOffset") {
            client.api.search3("some", artistOffset = artistOffset).execute()
        }
    }

    @Test
    fun `Should pass album count as request param`() {
        val albumCount = 21

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "albumCount=$albumCount") {
            client.api.search3("some", albumCount = albumCount).execute()
        }
    }

    @Test
    fun `Should pass album offset as request param`() {
        val albumOffset = 43

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "albumOffset=$albumOffset") {
            client.api.search3("some", albumOffset = albumOffset).execute()
        }
    }

    @Test
    fun `Should pass song count as request param`() {
        val songCount = 15

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "songCount=$songCount") {
            client.api.search3("some", songCount = songCount).execute()
        }
    }

    @Test
    fun `Should pass music folder id as request param`() {
        val musicFolderId = "43"

        mockWebServerRule.assertRequestParam(responseResourceName = "search3_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.search3("some", musicFolderId = musicFolderId).execute()
        }
    }
}
