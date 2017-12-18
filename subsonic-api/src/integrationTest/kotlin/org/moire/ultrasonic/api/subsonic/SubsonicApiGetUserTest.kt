package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
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

        response.user `should equal` User()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_user_ok.json")

        val response = client.api.getUser("some").execute()

        assertResponseSuccessful(response)
        with(response.body().user) {
            username `should equal to` "GodOfUniverse"
            email `should equal to` "some.mail@example.com"
            scrobblingEnabled `should equal to` false
            adminRole `should equal to` true
            settingsRole `should equal to` true
            downloadRole `should equal to` true
            uploadRole `should equal to` true
            playlistRole `should equal to` true
            coverArtRole `should equal to` true
            commentRole `should equal to` true
            podcastRole `should equal to` true
            streamRole `should equal to` true
            jukeboxRole `should equal to` true
            shareRole `should equal to` true
            videoConverstionRole `should equal to` false
            folderList.size `should equal to` 1
            folderList[0] `should equal to` 0
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
