package org.moire.ultrasonic.api.subsonic

import org.junit.Test

/**
 * Integration test for [SubsonicAPIDefinition.updateShare] call.
 */
class SubsonicApiUpdateShareTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.updateShare(11).execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.updateShare(12).execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id in request params`() {
        val id = 4432L

        mockWebServerRule.assertRequestParam(expectedParam = "id=$id") {
            client.api.updateShare(id = id).execute()
        }
    }

    @Test
    fun `Should pass description in request params`() {
        val description = "some-description"

        mockWebServerRule.assertRequestParam(expectedParam = "description=$description") {
            client.api.updateShare(123, description = description).execute()
        }
    }

    @Test
    fun `Should pass expires in request params`() {
        val expires = 223123123L

        mockWebServerRule.assertRequestParam(expectedParam = "expires=$expires") {
            client.api.updateShare(12, expires = expires).execute()
        }
    }
}
