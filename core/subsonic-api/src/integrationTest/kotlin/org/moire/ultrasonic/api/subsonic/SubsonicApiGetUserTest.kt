package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.User

/**
 * Integration test for [SubsonicAPIDefinition.getUser] call.
 */
class SubsonicApiGetUserTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getUser("some").execute()
        }

        response.user `should be equal to` User()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_user_ok.json")

        val response = client.api.getUser("some").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.user) {
            username `should be equal to` "GodOfUniverse"
            email `should be equal to` "some.mail@example.com"
            scrobblingEnabled `should be equal to` false
            adminRole `should be equal to` true
            settingsRole `should be equal to` true
            downloadRole `should be equal to` true
            uploadRole `should be equal to` true
            playlistRole `should be equal to` true
            coverArtRole `should be equal to` true
            commentRole `should be equal to` true
            podcastRole `should be equal to` true
            streamRole `should be equal to` true
            jukeboxRole `should be equal to` true
            shareRole `should be equal to` true
            videoConverstionRole `should be equal to` false
            folderList.size `should be equal to` 1
            folderList[0] `should be equal to` 0
        }
    }

    @Test
    fun `Should pass username in request param`() {
        val username = "Mighty"

        mockWebServerRule.assertRequestParam(expectedParam = "username=$username") {
            client.api.getUser(username).execute()
        }
    }
}
