package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIDefinition.deleteBookmark] call.
 */
class SubsonicApiDeleteBookmarkTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.deleteBookmark(1).execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.deleteBookmark(1).execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id in request params`() {
        val id = 233

        mockWebServerRule.assertRequestParam(expectedParam = "id=$id") {
            client.api.deleteBookmark(id).execute()
        }
    }
}
