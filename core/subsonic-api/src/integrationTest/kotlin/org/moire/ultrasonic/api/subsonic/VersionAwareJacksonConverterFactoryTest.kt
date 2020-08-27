package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be`
import org.junit.Before
import org.junit.Test

/**
 * Integration test for [VersionAwareJacksonConverterFactory].
 */
class VersionAwareJacksonConverterFactoryTest : SubsonicAPIClientTest()  {
    private val initialProtocolVersion = SubsonicAPIVersions.V1_1_0
    private var updatedProtocolVersion = SubsonicAPIVersions.V1_1_0

    @Before
    override fun setUp() {
        config = SubsonicClientConfiguration(
                mockWebServerRule.mockWebServer.url("/").toString(),
                USERNAME,
                PASSWORD,
                initialProtocolVersion,
                CLIENT_ID
        )
        client = SubsonicAPIClient(config)
    }

    @Test
    fun `Should update version from response`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.api.ping().execute()

        client.protocolVersion.`should be`(SubsonicAPIVersions.V1_13_0)
    }

    @Test
    fun `Should update version from response with utf-8 bom`() {
        mockWebServerRule.enqueueResponse("ping_ok_utf8_bom.json")

        client.api.ping().execute()

        client.protocolVersion.`should be`(SubsonicAPIVersions.V1_16_0)
    }

    @Test
    fun `Should not update version if response json doesn't contain version`() {
        mockWebServerRule.enqueueResponse("non_subsonic_response.json")

        client.api.stream("1234").execute()

        client.protocolVersion.`should be`(initialProtocolVersion)
    }
}