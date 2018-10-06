package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIDefinition.addChatMessage] call.
 */
class SubsonicApiAddChatMessageTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.addChatMessage("some").execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.addChatMessage("some").execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass message in request param`() {
        val message = "Youuhuuu"

        mockWebServerRule.assertRequestParam(expectedParam = "message=$message") {
            client.api.addChatMessage(message = message).execute()
        }
    }
}
