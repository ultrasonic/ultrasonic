package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should not be`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for [SubsonicAPIDefinition.stream] call.
 */
class SubsonicApiStreamTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle api error response`() {
        mockWebServerRule.enqueueResponse("request_data_not_found_error_response.json")

        val response = client.api.stream("some-id").execute().toStreamResponse()

        with(response) {
            stream `should be` null
            responseHttpCode `should be equal to` 200
            apiError `should be equal to` SubsonicError.RequestedDataWasNotFound
        }
    }

    @Test
    fun `Should handle server error`() {
        val httpErrorCode = 404
        mockWebServerRule.mockWebServer.enqueue(MockResponse().setResponseCode(httpErrorCode))

        val response = client.api.stream("some-id").execute().toStreamResponse()

        with(response) {
            stream `should be` null
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

        val response = client.api.stream("some-id").execute().toStreamResponse()

        with(response) {
            responseHttpCode `should be equal to` 200
            apiError `should be` null
            stream `should not be` null
            val expectedContent = mockWebServerRule.loadJsonResponse("ping_ok.json")
            stream!!.bufferedReader().readText() `should be equal to` expectedContent
        }
    }

    @Test
    fun `Should pass id as parameter`() {
        val id = "asdo123"

        mockWebServerRule.assertRequestParam("ping_ok.json", id) {
            client.api.stream(id = id).execute()
        }
    }

    @Test
    fun `Should pass max bit rate as param`() {
        val maxBitRate = 360

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "maxBitRate=$maxBitRate"
        ) {
            client.api.stream("some-id", maxBitRate = maxBitRate).execute()
        }
    }

    @Test
    fun `Should pass format as param`() {
        val format = "aac"

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "format=$format"
        ) {
            client.api.stream("some-id", format = format).execute()
        }
    }

    @Test
    fun `Should pass time offset as param`() {
        val timeOffset = 155

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "timeOffset=$timeOffset"
        ) {
            client.api.stream("some-id", timeOffset = timeOffset).execute()
        }
    }

    @Test
    fun `Should pass video size as param`() {
        val videoSize = "44144"

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "size=$videoSize"
        ) {
            client.api.stream("some-id", videoSize = videoSize).execute()
        }
    }

    @Test
    fun `Should pass estimate content length as param`() {
        val estimateContentLength = true

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "estimateContentLength=$estimateContentLength"
        ) {
            client.api.stream("some-id", estimateContentLength = estimateContentLength).execute()
        }
    }

    @Test
    fun `Should pass converted as param`() {
        val converted = false

        mockWebServerRule.assertRequestParam(
            "ping_ok.json",
            "converted=$converted"
        ) {
            client.api.stream("some-id", converted = converted).execute()
        }
    }
}
