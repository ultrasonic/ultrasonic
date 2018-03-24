package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for getLyrics() call.
 */
class SubsonicApiGetLyricsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        checkErrorCallParsed(mockWebServerRule) {
            client.api.getLyrics().execute()
        }
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_lyrics_ok.json")

        val response = client.api.getLyrics().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.lyrics) {
            artist `should equal to` "Amorphis"
            title `should equal to` "Alone"
            text `should equal to` "Tear dimmed rememberance\nIn a womb of time\nBreath upon " +
                    "me\nPossessed by the"
        }
    }

    @Test
    fun `Should pass artist param in request`() {
        val artist = "some-artist"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_lyrics_ok.json",
                expectedParam = "artist=$artist") {
            client.api.getLyrics(artist = artist).execute()
        }
    }

    @Test
    fun `Should pass title param in request`() {
        val title = "some-title"

        mockWebServerRule.assertRequestParam(responseResourceName = "get_lyrics_ok.json",
                expectedParam = "title=$title") {
            client.api.getLyrics(title = title).execute()
        }
    }
}
