package org.moire.ultrasonic.api.subsonic.interceptors

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonToken
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import java.io.IOException

private const val DEFAULT_PEEK_BYTE_COUNT = 1000L

/**
 * Special [Interceptor] that adds client supported version to request and tries to update it
 * from server response.
 *
 * Optionally [notifier] will be invoked on version change.
 *
 * @author Yahor Berdnikau
 */
internal class VersionInterceptor(
    internal var protocolVersion: SubsonicAPIVersions,
    private val notifier: (SubsonicAPIVersions) -> Unit = {}
) : Interceptor {
    private val jsonFactory = JsonFactory()

    override fun intercept(chain: Chain): okhttp3.Response {
        val originalRequest = chain.request()

        val newRequest = originalRequest.newBuilder()
                .url(originalRequest
                        .url()
                        .newBuilder()
                        .addQueryParameter("v", protocolVersion.restApiVersion)
                        .build())
                .build()

        val response = chain.proceed(newRequest)
        if (response.isSuccessful) {
            val isJson = response.body()?.contentType()?.subtype()?.equals("json", true) ?: false
            if (isJson) {
                tryUpdateProtocolVersion(response)
            }
        }

        return response
    }

    private fun tryUpdateProtocolVersion(response: Response) {
        val content = response.peekBody(DEFAULT_PEEK_BYTE_COUNT)
                .byteStream().bufferedReader().readText()

        try {
            val jsonReader = jsonFactory.createParser(content)
            jsonReader.nextToken()
            if (jsonReader.currentToken == JsonToken.START_OBJECT) {
                while (jsonReader.currentName != "version" &&
                        jsonReader.currentToken != null) {
                    jsonReader.nextToken()
                }
                val versionStr = jsonReader.nextTextValue()
                if (versionStr != null) {
                    try {
                        protocolVersion = SubsonicAPIVersions.fromApiVersion(versionStr)
                        notifier(protocolVersion)
                    } catch (e: IllegalArgumentException) {
                        // no-op
                    }
                }
            }
        } catch (io: IOException) {
            // no-op
        } catch (parse: JsonParseException) {
            // no-op
        }
    }
}
