package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index

/**
 * Integration test for [SubsonicAPIClient] for getIndexes() request.
 */
class SubsonicApiGetIndexesTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get indexes ok response`() {
        mockWebServerRule.enqueueResponse("get_indexes_ok.json")

        val response = client.api.getIndexes(null, null).execute()

        assertResponseSuccessful(response)
        response.body().indexes `should not be` null
        with(response.body().indexes) {
            lastModified `should equal` 1491069027523
            ignoredArticles `should equal` "The El La Los Las Le Les"
            shortcutList `should equal` listOf(
                    Artist(id = 889L, name = "podcasts"),
                    Artist(id = 890L, name = "audiobooks")
            )
            indexList `should equal` mutableListOf(
                    Index("A", listOf(
                            Artist(id = 50L, name = "Ace Of Base",
                                    starred = parseDate("2017-04-02T20:16:29.815Z")),
                            Artist(id = 379L, name = "A Perfect Circle")
                    )),
                    Index("H", listOf(
                            Artist(id = 299, name = "Haddaway"),
                            Artist(id = 297, name = "Halestorm")
                    ))
            )
        }
    }

    @Test
    fun `Should add music folder id as a query param for getIndexes api call`() {
        mockWebServerRule.enqueueResponse("get_indexes_ok.json")
        val musicFolderId = 9L

        client.api.getIndexes(musicFolderId, null).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
        }
    }

    @Test
    fun `Should add ifModifiedSince as a query param for getIndexes api call`() {
        mockWebServerRule.enqueueResponse("get_indexes_ok.json")
        val ifModifiedSince = System.currentTimeMillis()

        client.api.getIndexes(null, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should add both params to query for getIndexes api call`() {
        mockWebServerRule.enqueueResponse("get_indexes_ok.json")
        val musicFolderId = 110L
        val ifModifiedSince = System.currentTimeMillis()

        client.api.getIndexes(musicFolderId, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should parse get indexes error response`() {
        val response = checkErrorCallParsed(mockWebServerRule, {
            client.api.getIndexes(null, null).execute()
        })

        response.indexes `should not be` null
        with(response.indexes) {
            lastModified `should equal to` 0
            ignoredArticles `should equal to` ""
            indexList.size `should equal to` 0
            shortcutList.size `should equal to` 0
        }
    }
}
