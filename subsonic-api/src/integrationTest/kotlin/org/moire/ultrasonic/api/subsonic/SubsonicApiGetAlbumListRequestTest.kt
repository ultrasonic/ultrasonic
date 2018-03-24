package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.BY_GENRE
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration tests for [SubsonicAPIDefinition] for getAlbumList call.
 */
class SubsonicApiGetAlbumListRequestTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getAlbumList(BY_GENRE).execute()
        }

        response.albumList `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_album_list_ok.json")

        val response = client.api.getAlbumList(BY_GENRE).execute()

        assertResponseSuccessful(response)
        with(response.body()!!.albumList) {
            size `should equal to` 2
            this[1] `should equal` MusicDirectoryChild(id = "9997", parent = "9996", isDir = true,
                    title = "Endless Forms Most Beautiful", album = "Endless Forms Most Beautiful",
                    artist = "Nightwish", year = 2015, genre = "Symphonic Metal",
                    coverArt = "9997", playCount = 11,
                    created = parseDate("2017-09-02T16:22:49.000Z"))
        }
    }

    @Test
    fun `Should pass type in request params`() {
        val listType = AlbumListType.HIGHEST

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "type=${listType.typeName}") {
            client.api.getAlbumList(type = listType).execute()
        }
    }

    @Test
    fun `Should pass size in request params`() {
        val size = 45

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "size=$size") {
            client.api.getAlbumList(type = BY_GENRE, size = size).execute()
        }
    }

    @Test
    fun `Should pass offset in request params`() {
        val offset = 3

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "offset=$offset") {
            client.api.getAlbumList(type = BY_GENRE, offset = offset).execute()
        }
    }

    @Test
    fun `Should pass from year in request params`() {
        val fromYear = 2001

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "fromYear=$fromYear") {
            client.api.getAlbumList(type = BY_GENRE, fromYear = fromYear).execute()
        }
    }

    @Test
    fun `Should pass to year in request params`() {
        val toYear = 2017

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "toYear=$toYear") {
            client.api.getAlbumList(type = BY_GENRE, toYear = toYear).execute()
        }
    }

    @Test
    fun `Should pass genre in request params`() {
        val genre = "Rock"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "genre=$genre") {
            client.api.getAlbumList(type = BY_GENRE, genre = genre).execute()
        }
    }

    @Test
    fun `Should pass music folder id in request params`() {
        val folderId = "545"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_album_list_ok.json",
                expectedParam = "musicFolderId=$folderId") {
            client.api.getAlbumList(type = BY_GENRE, musicFolderId = folderId).execute()
        }
    }
}
