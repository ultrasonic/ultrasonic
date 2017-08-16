package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not contain`
import org.apache.commons.codec.binary.Hex
import org.junit.Test
import java.security.MessageDigest

/**
 * Integration test for [SubsonicAPIClient] that checks proper user password handling.
 */
class SubsonicApiPasswordTest : SubsonicAPIClientTest() {
    @Test
    fun `Should pass password hash and salt in query params for api version 1 13 0`() {
        val clientV12 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_14_0, CLIENT_ID)
        mockWebServerRule.enqueueResponse("ping_ok.json")

        clientV12.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "&s="
            requestLine `should contain` "&t="
            requestLine `should not contain` "&p=enc:"

            val salt = requestLine.split('&').find { it.startsWith("s=") }?.substringAfter('=')
            val token = requestLine.split('&').find { it.startsWith("t=") }?.substringAfter('=')
            val expectedToken = String(Hex.encodeHex(MessageDigest.getInstance("MD5")
                    .digest("$PASSWORD$salt".toByteArray()), false))
            token!! `should equal` expectedToken
        }
    }

    @Test
    fun `Should pass  hex encoded password in query params for api version 1 12 0`() {
        val clientV11 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_12_0, CLIENT_ID)
        mockWebServerRule.enqueueResponse("ping_ok.json")

        clientV11.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should not contain` "&s="
            requestLine `should not contain` "&t="
            requestLine `should contain` "&p=enc:"
            val passParam = requestLine.split('&').find { it.startsWith("p=enc:") }
            val encodedPassword = String(Hex.encodeHex(PASSWORD.toByteArray(), false))
            passParam `should equal` "p=enc:$encodedPassword"
        }
    }
}
