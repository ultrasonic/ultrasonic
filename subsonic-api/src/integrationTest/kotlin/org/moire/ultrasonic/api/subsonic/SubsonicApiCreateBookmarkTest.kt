package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIDefinition.createBookmark] call.
 */
class SubsonicApiCreateBookmarkTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.createBookmark(1, 1).execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.createBookmark(213, 123213L).execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id in request params`() {
        val id = 544

        mockWebServerRule.assertRequestParam(expectedParam = "id=$id") {
            client.api.createBookmark(id = id, position = 123).execute()
        }
    }

    @Test
    fun `Should pass position in request params`() {
        val position = 4412333L

        mockWebServerRule.assertRequestParam(expectedParam = "position=$position") {
            client.api.createBookmark(id = 12, position = position).execute()
        }
    }

    @Test
    fun `Should pass comment in request params`() {
        val comment = "some-comment"

        mockWebServerRule.assertRequestParam(expectedParam = "comment=$comment") {
            client.api.createBookmark(id = 1, position = 1, comment = comment).execute()
        }
    }
}
