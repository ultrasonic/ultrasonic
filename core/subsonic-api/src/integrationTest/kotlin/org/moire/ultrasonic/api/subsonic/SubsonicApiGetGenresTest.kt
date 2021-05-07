package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Genre

/**
 * Integration test for [SubsonicAPIDefinition.getGenres] call.
 */
class SubsonicApiGetGenresTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getGenres().execute()
        }

        response.genresList `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_genres_ok.json")

        val response = client.api.getGenres().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.genresList) {
            size `should be equal to` 5
            this[0] `should be equal to` Genre(1186, 103, "Rock")
            this[1] `should be equal to` Genre(896, 72, "Electronic")
            this[2] `should be equal to` Genre(790, 59, "Alternative Rock")
            this[3] `should be equal to` Genre(622, 97, "Trance")
            this[4] `should be equal to` Genre(476, 36, "Hard Rock")
        }
    }
}
