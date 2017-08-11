package org.moire.ultrasonic.api.subsonic

import okhttp3.mockwebserver.MockResponse
import okio.Okio
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should not contain`
import org.apache.commons.codec.binary.Hex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.License
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.MusicFolder
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule
import retrofit2.Response
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Integration test for [SubsonicAPIClient] class.
 */
@Suppress("TooManyFunctions")
class SubsonicAPIClientTest {
    companion object {
        const val USERNAME = "some-user"
        const val PASSWORD = "some-password"
        val CLIENT_VERSION = SubsonicAPIVersions.V1_13_0
        const val CLIENT_ID = "test-client"
    }

    @JvmField @Rule val mockWebServerRule = MockWebServerRule()

    private lateinit var client: SubsonicAPIClient

    @Before
    fun setUp() {
        client = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME, PASSWORD,
                CLIENT_VERSION, CLIENT_ID)
    }

    @Test
    fun `Should pass password hash and salt in query params for api version 1 13 0`() {
        val clientV12 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_14_0, CLIENT_ID)
        enqueueResponse("ping_ok.json")

        clientV12.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "&s="
            requestLine `should contain` "&t="
            requestLine `should not contain` "&p=enc:"

            val salt = requestLine.split('&').find { it.startsWith("s=") }?.substringAfter('=')
            val token = requestLine.split('&').find { it.startsWith("t=") }?.substringAfter('=')
            val expectedToken = String(Hex.encodeHex(MessageDigest.getInstance("MD5")
                    .digest("$PASSWORD$salt".toByteArray()), false))
            token!! `should equal` expectedToken
        }
    }

    @Test
    fun `Should pass  hex encoded password in query params for api version 1 12 0`() {
        val clientV11 = SubsonicAPIClient(mockWebServerRule.mockWebServer.url("/").toString(), USERNAME,
                PASSWORD, SubsonicAPIVersions.V1_12_0, CLIENT_ID)
        enqueueResponse("ping_ok.json")

        clientV11.api.ping().execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should not contain` "&s="
            requestLine `should not contain` "&t="
            requestLine `should contain` "&p=enc:"
            val passParam = requestLine.split('&').find { it.startsWith("p=enc:") }
            val encodedPassword = String(Hex.encodeHex(PASSWORD.toByteArray(), false))
            passParam `should equal` "p=enc:$encodedPassword"
        }
    }

    @Test
    fun `Should parse ping ok response`() {
        enqueueResponse("ping_ok.json")

        val response = client.api.ping().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
        }
    }

    @Test
    fun `Should parse ping error response`() {
        checkErrorCallParsed { client.api.ping().execute() }
    }

    @Test
    fun `Should parse get license ok response`() {
        enqueueResponse("license_ok.json")

        val response = client.api.getLicense().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
            license `should equal` License(valid = true,
                    trialExpires = parseDate("2016-11-23T20:17:15.206Z"),
                    email = "someone@example.net",
                    licenseExpires = parseDate("8994-08-17T07:12:55.807Z"))
        }
    }

    @Test
    fun `Should parse get license error response`() {
        val response = checkErrorCallParsed { client.api.getLicense().execute() }

        response.license `should not be` null
        with(response.license) {
            email `should equal to` ""
            valid `should equal to` false
        }
    }

    @Test
    fun `Should parse get music folders ok response`() {
        enqueueResponse("get_music_folders_ok.json")

        val response = client.api.getMusicFolders().execute()

        assertResponseSuccessful(response)
        with(response.body()) {
            assertBaseResponseOk()
            musicFolders `should equal` listOf(MusicFolder(0, "Music"), MusicFolder(2, "Test"))
        }
    }

    @Test
    fun `Should parse get music folders error response`() {
        val response = checkErrorCallParsed { client.api.getMusicFolders().execute() }

        response.musicFolders `should equal` emptyList()
    }

    @Test
    fun `Should parse get indexes ok response`() {
        // check for shortcut parsing
        enqueueResponse("get_indexes_ok.json")

        val response = client.api.getIndexes(null, null).execute()

        assertResponseSuccessful(response)
        response.body().indexes `should not be` null
        with(response.body().indexes) {
            lastModified `should equal` 1491069027523
            ignoredArticles `should equal` "The El La Los Las Le Les"
            shortcuts `should be` emptyList<Index>()
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

        client.api.getIndexes(musicFolderId, null).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
        }
    }

    @Test
    fun `Should add ifModifiedSince as a query param for getIndexes api call`() {
        enqueueResponse("get_indexes_ok.json")
        val ifModifiedSince = System.currentTimeMillis()

        client.api.getIndexes(null, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should add both params to query for getIndexes api call`() {
        enqueueResponse("get_indexes_ok.json")
        val musicFolderId = 110L
        val ifModifiedSince = System.currentTimeMillis()

        client.api.getIndexes(musicFolderId, ifModifiedSince).execute()

        with(mockWebServerRule.mockWebServer.takeRequest()) {
            requestLine `should contain` "musicFolderId=$musicFolderId"
            requestLine `should contain` "ifModifiedSince=$ifModifiedSince"
        }
    }

    @Test
    fun `Should parse get indexes error response`() {
        val response = checkErrorCallParsed { client.api.getIndexes(null, null).execute() }

        response.indexes `should not be` null
        with(response.indexes) {
            lastModified `should equal to` 0
            ignoredArticles `should equal to` ""
            indexList.size `should equal to` 0
            shortcuts.size `should equal to` 0
        }
    }

    @Test
    fun `Should parse getMusicDirectory error response`() {
        val response = checkErrorCallParsed { client.api.getMusicDirectory(1).execute() }

        response.musicDirectory `should be` null
    }

    @Test
    fun `GetMusicDirectory should add directory id to query params`() {
        enqueueResponse("get_music_directory_ok.json")
        val directoryId = 124L

        client.api.getMusicDirectory(directoryId).execute()

        mockWebServerRule.mockWebServer.takeRequest().requestLine `should contain` "id=$directoryId"
    }

    @Test
    fun `Should parse get music directory ok response`() {
        enqueueResponse("get_music_directory_ok.json")

        val response = client.api.getMusicDirectory(1).execute()

        assertResponseSuccessful(response)

        response.body().musicDirectory `should not be` null
        with(response.body().musicDirectory!!) {
            id `should equal` 382L
            name `should equal` "AC_DC"
            starred `should equal` parseDate("2017-04-02T20:16:29.815Z")
            childList.size `should be` 2
            childList[0] `should equal` MusicDirectoryChild(583L, 382L, true, "Black Ice",
                    "Black Ice", "AC/DC", 2008, "Hard Rock", 583L,
                    parseDate("2016-10-23T15:31:22.000Z"), parseDate("2017-04-02T20:16:15.724Z"))
            childList[1] `should equal` MusicDirectoryChild(582L, 382L, true, "Rock or Bust",
                    "Rock or Bust", "AC/DC", 2014, "Hard Rock", 582L,
                    parseDate("2016-10-23T15:31:24.000Z"), null)
        }
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
