package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Integration test for [SubsonicAPIClient] for setRating request.
 */
class SubsonicApiSetRatingTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse setRating ok response`() {
        val id = "110"
        val rating = 3

        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.setRating(id, rating).execute()

        assertResponseSuccessful(response)
        response.body()?.status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse setRating error response`() {
        val id = "110223"
        val rating = 5

        checkErrorCallParsed(mockWebServerRule) {
            client.api.setRating(id, rating).execute()
        }
    }

    @Test
    fun `Should pass id parameter`() {
        val id = "110"
        val rating = 5

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.setRating(id, rating).execute()
        }
    }

    @Test
    fun `Should pass rating parameter`() {
        val id = "110"
        val rating = 5

        mockWebServerRule.assertRequestParam(
            responseResourceName = "ping_ok.json",
            expectedParam = "rating=$rating"
        ) {
            client.api.setRating(id, rating).execute()
        }
    }
}
