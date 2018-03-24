package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.getVideos] call.
 */
class SubsonicApiGetVideosListTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getVideos().execute()
        }

        response.videosList `should equal` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_videos_ok.json")

        val response = client.api.getVideos().execute()

        assertResponseSuccessful(response)
        with(response.body()!!.videosList) {
            size `should equal to` 1
            this[0] `should equal` MusicDirectoryChild(id = "10402", parent = "10401",
                    isDir = false, title = "MVI_0512", album = "Incoming", size = 21889646,
                    contentType = "video/avi", suffix = "avi",
                    transcodedContentType = "video/x-flv", transcodedSuffix = "flv",
                    path = "Incoming/MVI_0512.avi", isVideo = true,
                    playCount = 0, created = parseDate("2017-11-19T12:34:33.000Z"),
                    type = "video")
        }
    }
}
