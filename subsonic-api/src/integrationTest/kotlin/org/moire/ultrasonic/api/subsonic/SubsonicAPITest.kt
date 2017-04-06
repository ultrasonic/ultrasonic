package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import okio.Okio
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.License
import org.moire.ultrasonic.api.subsonic.models.MusicFolder
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule
import retrofit2.Response
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

/**
 * Integration test for [SubsonicAPI] class.
 */
class SubsonicAPITest {
    companion object {
        val USERNAME = "some-user"
        val PASSWORD = "some-password"
        val CLIENT_VERSION = SubsonicAPIVersions.V1_13_0
        val CLIENT_ID = "test-client"
    }

    @JvmField
    @Rule
    val mockWebServerRule = MockWebServerRule()

    private lateinit var api: SubsonicAPI

    @Before
    fun setUp() {
        api = SubsonicAPI(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME, PASSWORD,
                CLIENT_VERSION, CLIENT_ID)
    }

    @Test
    fun `Should parse ping ok response`() {
        enqueueResponse("ping_ok.json")

        val response = api.getApi().ping().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
        }
    }

    @Test
    fun `Should parse ping error response`() {
        checkErrorCallParsed { api.getApi().ping().execute() }
    }

    @Test
    fun `Should parse get license ok response`() {
        enqueueResponse("license_ok.json")

        val response = api.getApi().getLicense().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
            license `should equal` License(true, parseDate("2016-11-23T20:17:15.206Z"))
        }
    }

    @Test
    fun `Should parse get license error response`() {
        val response = checkErrorCallParsed { api.getApi().getLicense().execute() }

        response.license `should be` null
    }

    @Test
    fun `Should parse get music folders ok response`() {
        enqueueResponse("get_music_directories_ok.json")

        val response = api.getApi().getMusicFolders().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
            musicFolders `should equal` listOf(MusicFolder(0, "Music"), MusicFolder(2, "Test"))
        }
    }

    @Test
    fun `Should parse get music folders error response`() {
        val response = checkErrorCallParsed { api.getApi().getMusicFolders().execute() }

        response.musicFolders `should be` null
    }

    @Test
    fun `Should parse get indexes ok response`() {
        // TODO: check for shortcut parsing
        enqueueResponse("get_indexes_ok.json")

        val response = api.getApi().getIndexes(null, null).execute()

        assertResponseSuccessful(response)
        response.body().indexes `should not be` null
        with(response.body().indexes!!) {
            lastModified `should equal` 1491069027523
            ignoredArticles `should equal` "The El La Los Las Le Les"
            shortcuts `should be` null
            indexList `should equal` mutableListOf(
                    Index("A", listOf(
                            Artist(50L, "Ace Of Base", parseDate("2017-04-02T20:16:29.815Z")),
                            Artist(379L, "A Perfect Circle", null)
                    )),
                    Index("H", listOf(
                            Artist(299, "Haddaway", null),
                            Artist(297, "Halestorm", null)
                    ))
            )
        }
    }

    @Test
    fun `Should add music folder id as a query param for getIndexes api call`() {
        enqueueResponse("get_indexes_ok.json")
        val musicFolderId = 9L

        api.getApi().getIndexes(musicFolderId, null).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
        }
    }

    @Test
    fun `Should add ifModifiedSince as a query param for getIndexes api call`() {
        enqueueResponse("get_indexes_ok.json")
        val ifModifiedSince = System.currentTimeMillis()

        api.getApi().getIndexes(null, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should add both params to query for getIndexes api call`() {
        enqueueResponse("get_indexes_ok.json")
        val musicFolderId = 110L
        val ifModifiedSince = System.currentTimeMillis()

        api.getApi().getIndexes(musicFolderId, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should parse get indexes error response`() {
        val response = checkErrorCallParsed { api.getApi().getIndexes(null, null).execute() }

        response.indexes `should be` null
    }

    private fun enqueueResponse(resourceName: String) {
        mockWebServerRule.mockWebServer.enqueue(MockResponse()
                .setBody(loadJsonResponse(resourceName)))
    }

    private fun loadJsonResponse(name: String): String {
        val source = Okio.buffer(Okio.source(javaClass.classLoader.getResourceAsStream(name)))
        return source.readString(Charset.forName("UTF-8"))
    }

    private fun <T> assertResponseSuccessful(response: Response<T>) {
        response.isSuccessful `should be` true
        response.body() `should not be` null
    }

    private fun parseDate(dateAsString: String): Calendar {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val result = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        result.time = dateFormat.parse(dateAsString.replace("Z$".toRegex(), "+0000"))

        return result
    }

    private fun <T: SubsonicResponse> checkErrorCallParsed(apiRequest: () -> Response<T>): T {
        enqueueResponse("generic_error_response.json")

        val response = apiRequest()

        assertResponseSuccessful(response)
        with(response.body()) {
            status `should be` SubsonicResponse.Status.ERROR
            error `should be` SubsonicError.GENERIC
        }
        return response.body()
    }

    private fun SubsonicResponse.assertBaseResponseOk() {
        status `should be` SubsonicResponse.Status.OK
        version `should be` SubsonicAPIVersions.V1_13_0
        error `should be` null
    }
}