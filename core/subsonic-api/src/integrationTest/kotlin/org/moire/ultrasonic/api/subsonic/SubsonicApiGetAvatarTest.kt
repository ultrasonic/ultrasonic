package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should not be`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient.getAvatar] call.
 */
class SubsonicApiGetAvatarTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle api error response`() {
        mockWebServerRule.enqueueResponse("request_data_not_found_error_response.json")

        val response = client.getAvatar("some")

        with(response) {
            stream `should be` null
            responseHttpCode `should be equal to` 200
            apiError `should be equal to` SubsonicError.RequestedDataWasNotFound
        }
    }

    @Test
    fun `Should handle server error`() {
        val httpErrorCode = 500
        mockWebServerRule.mockWebServer.enqueue(MockResponse().setResponseCode(httpErrorCode))

        val response = client.getAvatar("some")

        with(response) {
            stream `should be equal to` null
            responseHttpCode `should be equal to` httpErrorCode
            apiError `should be` null
        }
    }

    @Test
    fun `Should return successful call stream`() {
        mockWebServerRule.mockWebServer.enqueue(
            MockResponse()
                .setBody(mockWebServerRule.loadJsonResponse("ping_ok.json"))
        )

        val response = client.stream("some")

        with(response) {
            responseHttpCode `should be equal to` 200
            apiError `should be` null
            stream `should not be` null
            val expectedContent = mockWebServerRule.loadJsonResponse("ping_ok.json")
            stream!!.bufferedReader().readText() `should be equal to` expectedContent
        }
    }

    @Test
    fun `Should pass username as param`() {
        val username = "Guardian"

        mockWebServerRule.assertRequestParam(expectedParam = "username=$username") {
            client.api.getAvatar(username).execute()
        }
    }
}
