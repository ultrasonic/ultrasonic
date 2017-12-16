package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes

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
                    Artist(id = "889", name = "podcasts"),
                    Artist(id = "890", name = "audiobooks")
            )
            indexList `should equal` mutableListOf(
                    Index("A", listOf(
                            Artist(id = "50", name = "Ace Of Base",
                                    starred = parseDate("2017-04-02T20:16:29.815Z")),
                            Artist(id = "379", name = "A Perfect Circle")
                    )),
                    Index("H", listOf(
                            Artist(id = "299", name = "Haddaway"),
                            Artist(id = "297", name = "Halestorm")
                    ))
            )
        }
    }

    @Test
    fun `Should add music folder id as a query param for getIndexes api call`() {
        val musicFolderId = "9"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_indexes_ok.json",
                expectedParam = "musicFolderId=$musicFolderId") {
            client.api.getIndexes(musicFolderId, null).execute()
        }
    }

    @Test
    fun `Should add ifModifiedSince as a query param for getIndexes api call`() {
        val ifModifiedSince = System.currentTimeMillis()

        mockWebServerRule.assertRequestParam(responseResourceName = "get_indexes_ok.json",
                expectedParam = "ifModifiedSince=$ifModifiedSince") {
            client.api.getIndexes(null, ifModifiedSince).execute()
        }
    }

    @Test
    fun `Should parse get indexes error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getIndexes(null, null).execute()
        }

        response.indexes `should not be` null
        response.indexes `should equal` Indexes()
    }
}
