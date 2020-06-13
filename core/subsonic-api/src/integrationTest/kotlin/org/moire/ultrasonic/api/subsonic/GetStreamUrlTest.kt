package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_6_0
import org.moire.ultrasonic.api.subsonic.interceptors.toHexBytes
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule

/**
 * Integration test for [SubsonicAPIClient.getStreamUrl] method.
 */
class GetStreamUrlTest {
    @JvmField @Rule val mockWebServerRule = MockWebServerRule()

    val id = "boom"
    private lateinit var client: SubsonicAPIClient
    private lateinit var expectedUrl: String

    @Before
    fun setUp() {
        val config = SubsonicClientConfiguration(
            mockWebServerRule.mockWebServer.url("/").toString(),
            USERNAME,
            PASSWORD,
            V1_6_0,
            CLIENT_ID
        )
        client = SubsonicAPIClient(config)
        val baseExpectedUrl = mockWebServerRule.mockWebServer.url("").toString()
        expectedUrl = "$baseExpectedUrl/rest/stream.view?id=$id&u=$USERNAME" +
            "&c=$CLIENT_ID&f=json&v=${V1_6_0.restApiVersion}&p=enc:${PASSWORD.toHexBytes()}"
    }

    @Test
    fun `Should return valid stream url`() {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val streamUrl = client.getStreamUrl(id)

        streamUrl `should be equal to` expectedUrl
    }

    @Test
    fun `Should still return stream url if connection failed`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val streamUrl = client.getStreamUrl(id)

        streamUrl `should be equal to` expectedUrl
    }
}
