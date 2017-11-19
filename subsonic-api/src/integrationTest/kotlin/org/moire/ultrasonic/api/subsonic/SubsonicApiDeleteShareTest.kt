package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIDefinition.deleteShare] call.
 */
class SubsonicApiDeleteShareTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.deleteShare(123).execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.deleteShare(12).execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id in request params`() {
        val id = 224L

        mockWebServerRule.assertRequestParam(expectedParam = "id=$id") {
            client.api.deleteShare(id).execute()
        }
    }
}
