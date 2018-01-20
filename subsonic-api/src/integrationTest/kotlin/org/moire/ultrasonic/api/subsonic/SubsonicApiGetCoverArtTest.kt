package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for [SubsonicAPIDefinition.getCoverArt] call.
 */
class SubsonicApiGetCoverArtTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle api error response`() {
        mockWebServerRule.enqueueResponse("request_data_not_found_error_response.json")

        val response = client.getCoverArt("some-id")

        with(response) {
            stream `should be` null
            responseHttpCode `should equal to` 200
            apiError `should equal` SubsonicError.RequestedDataWasNotFound
        }
    }

    @Test
    fun `Should handle server error`() {
        val httpErrorCode = 404
        mockWebServerRule.mockWebServer.enqueue(MockResponse().setResponseCode(httpErrorCode))

        val response = client.getCoverArt("some-id")

        with(response) {
            stream `should be` null
            responseHttpCode `should equal` 404
            apiError `should be` null
        }
    }

    @Test
    fun `Should return successful call stream`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse()
                .setBody(mockWebServerRule.loadJsonResponse("ping_ok.json")))

        val response = client.getCoverArt("some-id")

        with(response) {
            responseHttpCode `should equal to` 200
            apiError `should be` null
            stream `should not be` null
            val expectedContent = mockWebServerRule.loadJsonResponse("ping_ok.json")
            stream!!.bufferedReader().readText() `should equal to` expectedContent
        }
    }

    @Test
    fun `Should pass id as parameter`() {
        val id = "ca123994"

        mockWebServerRule.assertRequestParam("ping_ok.json", id) {
            client.api.getCoverArt(id).execute()
        }
    }

    @Test
    fun `Should pass size as a parameter`() {
        val size = 45600L

        mockWebServerRule.assertRequestParam("ping_ok.json", size.toString()) {
            client.api.getCoverArt("some-id", size).execute()
        }
    }
}
