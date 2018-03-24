package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.ChatMessage

/**
 * Integration test for [SubsonicAPIDefinition.getChatMessages] call.
 */
class SubsonicApiGetChatMessagesTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getChatMessages().execute()
        }

        response.chatMessages `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_chat_messages_ok.json")

        val response = client.api.getChatMessages().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.chatMessages) {
            size `should equal to` 2
            this[0] `should equal` ChatMessage(username = "sindre", time = 1269771845310,
                    message = "Sindre was here")
            this[1] `should equal` ChatMessage(username = "ben", time = 1269771842504,
                    message = "Ben too")
        }
    }

    @Test
    fun `Should pass since in request param`() {
        val since = 21388L

        mockWebServerRule.assertRequestParam(expectedParam = "since=$since") {
            client.api.getChatMessages(since = since).execute()
        }
    }
}
