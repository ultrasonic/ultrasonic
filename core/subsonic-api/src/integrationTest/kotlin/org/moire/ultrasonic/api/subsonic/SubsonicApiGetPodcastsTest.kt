package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getPodcasts call.
 */
class SubsonicApiGetPodcastsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() {
        val response = checkErrorCallParsed(mockWebServerRule) {
            client.api.getPodcasts().execute()
        }

        response.podcastChannels `should not be` null
        response.podcastChannels `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() {
        mockWebServerRule.enqueueResponse("get_podcasts_ok.json")

        val response = client.api.getPodcasts().execute()

        assertResponseSuccessful(response)
        val podcastChannelsList = response.body()!!.podcastChannels
        podcastChannelsList.size `should be equal to` 1
        with(podcastChannelsList[0]) {
            id `should be equal to` "2"
            url `should be equal to` "http://feeds.codenewbie.org/cnpodcast.xml"
            title `should be equal to` "CodeNewbie"
            description `should be equal to` "Stories and interviews from people on their coding " +
                "journey."
            coverArt `should be equal to` "pod-2"
            originalImageUrl `should be equal to` "http://codenewbie.blubrry.com/wp-content/" +
                "uploads/powerpress/220808.jpg"
            status `should be equal to` "completed"
            errorMessage `should be equal to` ""
            episodeList.size `should be equal to` 10
            episodeList[0] `should be equal to` MusicDirectoryChild(
                id = "148", parent = "9959",
                isDir = false,
                title = "S1:EP3 – How to teach yourself computer science (Vaidehi Joshi)",
                album = "CodeNewbie", artist = "podcasts", coverArt = "9959",
                size = 38274221, contentType = "audio/mpeg", suffix = "mp3",
                duration = 2397, bitRate = 128, isVideo = false, playCount = 0,
                created = parseDate("2017-08-30T09:33:39.000Z"), type = "podcast",
                streamId = "9982", channelId = "2",
                description = "Vaidehi decided to take on a year-long challenge. " +
                    "She'd pick a computer science topic every week, do tons of research " +
                    "and write a technical blog post explaining it in simple terms and " +
                    "beautiful illustrations. And then she actually did it. She tells us " +
                    "about her project, basecs, how it's changed her as a developer, and " +
                    "how she handles the trolls and negativity from people who don't " +
                    "appreciate her work. Show Notes Technical Writer position at " +
                    "CodeNewbie basecs 100 Days of Code Conway's Game of Life Hexes and " +
                    "Other Magical Numbers (Vaidehi's blog post) Bits, Bytes, Building " +
                    "With Binary (Vaidehi's blog post) Rust",
                status = "completed",
                publishDate = parseDate("2017-08-29T00:01:01.000Z")
            )
        }
    }

    @Test
    fun `Should pass include episodes in request`() {
        val includeEpisodes = true

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_podcasts_ok.json",
            expectedParam = "includeEpisodes=$includeEpisodes"
        ) {
            client.api.getPodcasts(includeEpisodes = includeEpisodes).execute()
        }
    }

    @Test
    fun `Should pass id in request param`() {
        val id = "249"

        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_podcasts_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.getPodcasts(id = id).execute()
        }
    }
}
