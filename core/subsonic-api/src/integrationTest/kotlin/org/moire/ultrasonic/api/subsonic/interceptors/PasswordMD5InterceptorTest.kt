package org.moire.ultrasonic.api.subsonic.interceptors

import java.security.MessageDigest
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not contain`
import org.apache.commons.codec.binary.Hex
import org.junit.Test

/**
 * Integration test for [PasswordMD5Interceptor].
 */
class PasswordMD5InterceptorTest : BaseInterceptorTest() {
    private val password = "some-password"
    override val interceptor: Interceptor get() = PasswordMD5Interceptor(password)

    @Test
    fun `Should pass password hash and salt in query params`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse())
        val request = createRequest { }

        client.newCall(request).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "s="
            requestLine `should contain` "t="
            requestLine `should not contain` "p=enc:"

            val salt = requestLine.split('&').find { it.startsWith("s=") }
                ?.substringAfter('=')?.substringBefore(" ")
            val expectedToken = String(
                Hex.encodeHex(
                    MessageDigest.getInstance("MD5")
                        .digest("$password$salt".toByteArray()),
                    true
                )
            )
            requestLine `should contain` "t=$expectedToken"
        }
    }
}
