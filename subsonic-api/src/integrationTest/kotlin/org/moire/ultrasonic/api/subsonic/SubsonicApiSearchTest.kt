package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import java.util.Calendar

/**
 * Integration test for [SubsonicAPIClient] for search call.
 */
class SubsonicApiSearchTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error response`() {
        checkErrorCallParsed(mockWebServerRule, {
            client.api.search().execute()
        })
    }

    @Test
    fun `Should parse ok response`() {
        enqueueOkResponse()

        val response = client.api.search().execute()

        assertResponseSuccessful(response)
        with(response.body().searchResult) {
            offset `should equal to` 10
            totalHits `should equal to` 53
            matchList.size `should equal to` 1
            matchList[0] `should equal` MusicDirectoryChild(id = 5831L, parent = 5766L,
                    isDir = false, title = "You'll Be Under My Wheels",
                    album = "Need for Speed Most Wanted", artist = "The Prodigy",
                    track = 17, year = 2005, genre = "Rap", coverArt = "5766",
                    size = 5607024, contentType = "audio/mpeg", suffix = "mp3", duration = 233,
                    bitRate = 192,
                    path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels.mp3",
                    isVideo = false, playCount = 0, discNumber = 1,
                    created = parseDate("2016-10-23T20:09:02.000Z"), albumId = 568,
                    artistId = 505, type = "music")
        }
    }

    @Test
    fun `Should pass artist param`() {
        enqueueOkResponse()
        val artist = "some-artist"
        client.api.search(artist = artist).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "artist=$artist"
    }

    @Test
    fun `Should pass album param`() {
        enqueueOkResponse()
        val album = "some-album"
        client.api.search(album = album).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "album=$album"
    }

    @Test
    fun `Should pass title param`() {
        enqueueOkResponse()
        val title = "some-title"
        client.api.search(title = title).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "title=$title"
    }

    @Test
    fun `Should contain any param`() {
        enqueueOkResponse()
        val any = "AnyString"
        client.api.search(any = any).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "any=$any"
    }

    @Test
    fun `Should contain count param`() {
        enqueueOkResponse()
        val count = 11
        client.api.search(count = count).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "count=$count"
    }

    @Test
    fun `Should contain offset param`() {
        enqueueOkResponse()
        val offset = 54
        client.api.search(offset = offset).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "offset=$offset"
    }

    @Test
    fun `Should contain newerThan param`() {
        enqueueOkResponse()
        val newerThan = Calendar.getInstance()
        client.api.search(newerThan = newerThan.time.time).execute()

        val request = mockWebServerRule.mockWebServer.takeRequest()

        request.requestLine `should contain` "newerThan=${newerThan.time.time}"
    }

    private fun enqueueOkResponse() {
        mockWebServerRule.enqueueResponse("search_ok.json")
    }
}
