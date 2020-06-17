package org.moire.ultrasonic.api.subsonic

import java.util.Calendar
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for scrobble call.
 */
class SubsonicApiScrobbleTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.scrobble("id").execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.scrobble("id").execute()

        assertResponseSuccessful(response)
    }

    @Test
    fun `Should pass id param in request`() {
        val id = "some-id"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.scrobble(id = id).execute()
        }
    }

    @Test
    fun `Should pass time param in request`() {
        val time = Calendar.getInstance().timeInMillis

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "time=$time"
        ) {
            client.api.scrobble(id = "some-id", time = time).execute()
        }
    }

    @Test
    fun `Should pass submission param in request`() {
        val submission = false

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "submission=$submission"
        ) {
            client.api.scrobble(id = "some-id", submission = submission).execute()
        }
    }
}
