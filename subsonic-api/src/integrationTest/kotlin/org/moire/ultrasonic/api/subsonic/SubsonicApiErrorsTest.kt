package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should throw`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicError.Generic
import org.moire.ultrasonic.api.subsonic.SubsonicError.WrongUsernameOrPassword
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Response
import java.io.IOException

/**
 * Integration test that checks validity of api errors parsing.
 */
class SubsonicApiErrorsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse wrong username or password error`() {
        mockWebServerRule.enqueueResponse("wrong_username_or_password_error.json")

        val response = client.api.ping().execute()

        response.assertError(WrongUsernameOrPassword)
    }

    @Test
    fun `Should parse generic error with message`() {
        mockWebServerRule.enqueueResponse("generic_error.json")

        val response = client.api.ping().execute()

        response.assertError(Generic("Some generic error message."))
    }

    @Test
    fun `Should fail on unknown error`() {
        mockWebServerRule.enqueueResponse("unexpected_error.json")

        val fail = {
            client.api.ping().execute()
        }

        fail `should throw` IOException::class
    }

    private fun Response<SubsonicResponse>.assertError(expectedError: SubsonicError) =
            with(body()) {
                error `should not be` null
                error `should equal` expectedError
            }
}
