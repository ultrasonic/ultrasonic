package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Response

/**
 * Converts a Response to a StreamResponse
 */
fun Response<out ResponseBody>.toStreamResponse(): StreamResponse {
    val response = this
    return if (response.isSuccessful) {
        val responseBody = response.body()
        val contentType = responseBody?.contentType()
        if (
            contentType != null &&
            contentType.type().equals("application", true) &&
            contentType.subtype().equals("json", true)
        ) {
            val error = SubsonicAPIClient.jacksonMapper.readValue<SubsonicResponse>(
                responseBody.byteStream()
            )
            StreamResponse(apiError = error.error, responseHttpCode = response.code())
        } else {
            StreamResponse(
                stream = responseBody?.byteStream(),
                responseHttpCode = response.code()
            )
        }
    } else {
        StreamResponse(responseHttpCode = response.code())
    }
}

/**
 * This call wraps Subsonic API calls so their results can be checked for errors, API version, etc
 * It creates Exceptions from the results returned by the Subsonic API
 */
@Suppress("ThrowsCount")
fun <T : SubsonicResponse> Response<out T>.throwOnFailure(): Response<out T> {
    val response = this

    if (response.isSuccessful && response.body()!!.status === SubsonicResponse.Status.OK) {
        return this as Response<T>
    }
    if (!response.isSuccessful) {
        throw IOException("Server error, code: " + response.code())
    } else if (
        response.body()!!.status === SubsonicResponse.Status.ERROR &&
        response.body()!!.error != null
    ) {
        throw SubsonicRESTException(response.body()!!.error!!)
    } else {
        throw IOException("Failed to perform request: " + response.code())
    }
}

fun Response<out SubsonicResponse>.falseOnFailure(): Boolean {
    return (this.isSuccessful && this.body()!!.status === SubsonicResponse.Status.OK)
}

/**
 * This call wraps Subsonic API calls so their results can be checked for errors, API version, etc
 * It creates Exceptions from a StreamResponse
 */
fun StreamResponse.throwOnFailure(): StreamResponse {
    val response = this
    if (response.hasError() || response.stream == null) {
        if (response.apiError != null) {
            throw SubsonicRESTException(response.apiError)
        } else {
            throw IOException(
                "Failed to make endpoint request, code: " + response.responseHttpCode
            )
        }
    }
    return this
}

/**
 * Gets a stream url.
 *
 * Calling this method do actual connection to the backend, though not downloading all content.
 *
 * Consider do not use this method, but [SubsonicAPIDefinition.stream] call.
 */
fun SubsonicAPIDefinition.getStreamUrl(id: String): String {
    val response = this.stream(id, format = "raw").execute()
    val url = response.raw().request().url().toString()
    if (response.isSuccessful) {
        response.body()?.close()
    }
    return url
}
