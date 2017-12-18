package org.moire.ultrasonic.api.subsonic.interceptors

import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not contain`
import org.apache.commons.codec.binary.Hex
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.PASSWORD

/**
 * Integration test for [PasswordHexInterceptor].
 */
class PasswordHexInterceptorTest : BaseInterceptorTest() {
    private val password = "some-password"

    override val interceptor: Interceptor get() = PasswordHexInterceptor(password)

    @Test
    fun `Should pass hex encoded password in query params`() {
        mockWebServerRule.mockWebServer.enqueue(MockResponse())
        val request = createRequest { }

        client.newCall(request).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should not contain` "s="
            requestLine `should not contain` "t="
            val encodedPassword = String(Hex.encodeHex(PASSWORD.toByteArray(), false))
            requestLine `should contain` "p=enc:$encodedPassword"
        }
    }
}
