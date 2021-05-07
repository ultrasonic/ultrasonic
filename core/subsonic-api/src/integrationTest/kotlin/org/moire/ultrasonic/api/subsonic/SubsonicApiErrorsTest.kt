package org.moire.ultrasonic.api.subsonic

import java.io.IOException
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should throw`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicError.Generic
import org.moire.ultrasonic.api.subsonic.SubsonicError.IncompatibleClientProtocolVersion
import org.moire.ultrasonic.api.subsonic.SubsonicError.IncompatibleServerProtocolVersion
import org.moire.ultrasonic.api.subsonic.SubsonicError.RequestedDataWasNotFound
import org.moire.ultrasonic.api.subsonic.SubsonicError.RequiredParamMissing
import org.moire.ultrasonic.api.subsonic.SubsonicError.TokenAuthNotSupportedForLDAP
import org.moire.ultrasonic.api.subsonic.SubsonicError.TrialPeriodIsOver
import org.moire.ultrasonic.api.subsonic.SubsonicError.UserNotAuthorizedForOperation
import org.moire.ultrasonic.api.subsonic.SubsonicError.WrongUsernameOrPassword
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Response

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

    @Test
    fun `Should parse required param missing error`() {
        mockWebServerRule.enqueueResponse("required_param_missing_error.json")

        val response = client.api.ping().execute()

        response.assertError(RequiredParamMissing)
    }

    @Test
    fun `Should parse incompatible client protocol version error`() {
        mockWebServerRule.enqueueResponse("incompatible_client_protocol_version_error.json")

        val response = client.api.ping().execute()

        response.assertError(IncompatibleClientProtocolVersion)
    }

    @Test
    fun `Should parse incompatible server protocol version error`() {
        mockWebServerRule.enqueueResponse("incompatible_server_protocol_version_error.json")

        val response = client.api.ping().execute()

        response.assertError(IncompatibleServerProtocolVersion)
    }

    @Test
    fun `Should parse token auth not supported for ldap error`() {
        mockWebServerRule.enqueueResponse("token_auth_not_supported_for_ldap_error.json")

        val response = client.api.ping().execute()

        response.assertError(TokenAuthNotSupportedForLDAP)
    }

    @Test
    fun `Should parse user not authorized for operation error`() {
        mockWebServerRule.enqueueResponse("user_not_authorized_for_operation_error.json")

        val response = client.api.ping().execute()

        response.assertError(UserNotAuthorizedForOperation)
    }

    @Test
    fun `Should parse trial period is over error`() {
        mockWebServerRule.enqueueResponse("trial_period_is_over_error.json")

        val response = client.api.ping().execute()

        response.assertError(TrialPeriodIsOver)
    }

    @Test
    fun `Should parse requested data was not found error`() {
        mockWebServerRule.enqueueResponse("requested_data_was_not_found_error.json")

        val response = client.api.ping().execute()

        response.assertError(RequestedDataWasNotFound)
    }

    @Test
    fun `Should parse error with reversed tokens order`() {
        mockWebServerRule.enqueueResponse("reversed_tokens_generic_error.json")

        val response = client.api.ping().execute()

        response.assertError(Generic("Video streaming not supported"))
    }

    @Test
    fun `Should parse error if json contains error first before other fields`() {
        mockWebServerRule.enqueueResponse("error_first_generic_error.json")

        val response = client.api.ping().execute()

        response.assertError(Generic("Video streaming not supported"))
    }

    @Test
    fun `Should parse error if json doesn't contain message field`() {
        mockWebServerRule.enqueueResponse("without_message_generic_error.json")

        val response = client.api.ping().execute()

        response.assertError(Generic(""))
    }

    @Test
    fun `Should parse error if error json contains additional object`() {
        mockWebServerRule.enqueueResponse("with_additional_json_object_generic_error.json")

        val response = client.api.ping().execute()

        response.assertError(Generic(""))
    }

    private fun Response<SubsonicResponse>.assertError(expectedError: SubsonicError) =
        with(body()!!) {
            error `should not be` null
            error `should be equal to` expectedError
        }
}
