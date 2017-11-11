package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
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

        response.genresList `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_genres_ok.json")

        val response = client.api.getGenres().execute()

        assertResponseSuccessful(response)
        with(response.body().genresList) {
            size `should equal to` 5
            this[0] `should equal` Genre(1186, 103, "Rock")
            this[1] `should equal` Genre(896, 72, "Electronic")
            this[2] `should equal` Genre(790, 59, "Alternative Rock")
            this[3] `should equal` Genre(622, 97, "Trance")
            this[4] `should equal` Genre(476, 36, "Hard Rock")
        }
    }
}
