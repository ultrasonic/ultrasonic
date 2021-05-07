package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.License

/**
 * Integration test [SubsonicAPIClient] for getLicense() request.
 */
class SubsonicApiGetLicenseTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get license ok response`() {
        mockWebServerRule.enqueueResponse("license_ok.json")

        val response = client.api.getLicense().execute()

        assertResponseSuccessful(response)
        with(response.body()!!) {
            assertBaseResponseOk()
            license `should be equal to` License(
                valid = true,
                trialExpires = parseDate("2016-11-23T20:17:15.206Z"),
                email = "someone@example.net",
                licenseExpires = parseDate("8994-08-17T07:12:55.807Z")
            )
        }
    }

    @Test
    fun `Should parse get license error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getLicense().execute()
        }

        response.license `should not be` null
        response.license.email `should be equal to` License().email
        response.license.valid `should be equal to` License().valid
    }
}
