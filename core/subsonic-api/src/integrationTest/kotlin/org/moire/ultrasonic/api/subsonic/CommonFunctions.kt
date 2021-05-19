package org.moire.ultrasonic.api.subsonic

import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Okio
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not be`
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule
import retrofit2.Response

const val USERNAME = "some-user"
const val PASSWORD = "some-password"
val CLIENT_VERSION = SubsonicAPIVersions.V1_16_0
const val CLIENT_ID = "test-client"

val dateFormat by lazy(
    LazyThreadSafetyMode.NONE
) {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
}

fun MockWebServerRule.enqueueResponse(resourceName: String) {
    mockWebServer.enqueueResponse(resourceName)
}

fun MockWebServer.enqueueResponse(resourceName: String) {
    enqueue(
        MockResponse()
            .setBody(loadJsonResponse(resourceName))
            .setHeader("Content-Type", "application/json;charset=UTF-8")
    )
}

fun Any.loadJsonResponse(name: String): String {
    val source = Okio.buffer(Okio.source(javaClass.classLoader.getResourceAsStream(name)))
    return source.readString(Charset.forName("UTF-8"))
}

fun Any.loadResourceStream(name: String): InputStream {
    val source = Okio.buffer(Okio.source(javaClass.classLoader.getResourceAsStream(name)))
    return source.inputStream()
}

fun <T> assertResponseSuccessful(response: Response<T>) {
    response.isSuccessful `should be` true
    response.body() `should not be` null
}

fun parseDate(dateAsString: String): Calendar {
    val result = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    result.time = dateFormat.parse(dateAsString.replace("Z$".toRegex(), "+0000"))

    return result
}

fun <T : SubsonicResponse> checkErrorCallParsed(
    mockWebServerRule: MockWebServerRule,
    apiRequest: () -> Response<T>
): T {
    mockWebServerRule.enqueueResponse("request_data_not_found_error_response.json")

    val response = apiRequest()

    assertResponseSuccessful(response)
    with(response.body()!!) {
        status `should be` SubsonicResponse.Status.ERROR
        error `should be` SubsonicError.RequestedDataWasNotFound
    }
    return response.body()!!
}

fun SubsonicResponse.assertBaseResponseOk() {
    status `should be` SubsonicResponse.Status.OK
    version `should be` SubsonicAPIVersions.V1_13_0
    error `should be` null
}

fun MockWebServerRule.assertRequestParam(
    responseResourceName: String = "ping_ok.json",
    expectedParam: String,
    apiRequest: () -> Response<out Any>
) {
    this.enqueueResponse(responseResourceName)
    apiRequest()

    val request = this.mockWebServer.takeRequest()

    request.requestLine `should contain` expectedParam
}
