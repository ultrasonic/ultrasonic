package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.STARRED

/**
 * Integration test for [SubsonicAPIClient] for getAlbumList2() call.
 */
@Suppress("NamingConventionViolation")
class SubsonicApiGetAlbumList2Test : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getAlbumList2(STARRED).execute()
        }

        response.albumList `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_album_list_2_ok.json")

        val response = client.api.getAlbumList2(STARRED).execute()

        assertResponseSuccessful(response)
        with(response.body()!!.albumList) {
            this.size `should be equal to` 2
            this[0] `should be equal to` Album(
                id = "962", name = "Fury", artist = "Sick Puppies",
                artistId = "473", coverArt = "al-962", songCount = 13, duration = 2591,
                created = parseDate("2017-09-02T17:34:51.000Z"), year = 2016,
                genre = "Alternative Rock"
            )
            this[1] `should be equal to` Album(
                id = "961", name = "Endless Forms Most Beautiful",
                artist = "Nightwish", artistId = "559", coverArt = "al-961", songCount = 22,
                duration = 9469, created = parseDate("2017-09-02T16:22:47.000Z"),
                year = 2015, genre = "Symphonic Metal"
            )
        }
    }

    @Test
    fun `Should pass type in request params`() {
        val type = AlbumListType.SORTED_BY_NAME

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "type=${type.typeName}"
        ) {
            client.api.getAlbumList2(type = type).execute()
        }
    }

    @Test
    fun `Should pass size in request param`() {
        val size = 45

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "size=$size"
        ) {
            client.api.getAlbumList2(STARRED, size = size).execute()
        }
    }

    @Test
    fun `Should pass offset in request param`() {
        val offset = 33

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "offset=$offset"
        ) {
            client.api.getAlbumList2(STARRED, offset = offset).execute()
        }
    }

    @Test
    fun `Should pass from year in request params`() {
        val fromYear = 3030

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "fromYear=$fromYear"
        ) {
            client.api.getAlbumList2(STARRED, fromYear = fromYear).execute()
        }
    }

    @Test
    fun `Should pass toYear in request param`() {
        val toYear = 2014

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "toYear=$toYear"
        ) {
            client.api.getAlbumList2(STARRED, toYear = toYear).execute()
        }
    }

    @Test
    fun `Should pass genre in request param`() {
        val genre = "MathRock"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "genre=$genre"
        ) {
            client.api.getAlbumList2(STARRED, genre = genre).execute()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() {
        val musicFolderId = "9422"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_album_list_2_ok.json",
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.getAlbumList2(STARRED, musicFolderId = musicFolderId).execute()
        }
    }
}
