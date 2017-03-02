package org.moire.ultrasonic.api.subsonic

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import org.moire.ultrasonic.api.subsonic.models.SubsonicResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigInteger

/**
 * Main entry for Subsonic API calls.
 *
 * @see SubsonicAPI http://www.subsonic.org/pages/api.jsp
 */
class SubsonicAPI(baseUrl: String,
                  username: String,
                  private val password: String,
                  clientProtocolVersion: SubsonicAPIVersions,
                  clientID: String) {
    private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Adds default request params
                val originalRequest = chain.request()
                val newUrl = originalRequest.url().newBuilder()
                        .addQueryParameter("u", username)
                        .addQueryParameter("p", passwordHex())
                        .addQueryParameter("v", clientProtocolVersion.restApiVersion)
                        .addQueryParameter("c", clientID)
                        .addQueryParameter("f", "json")
                        .build()
                chain.proceed(originalRequest.newBuilder().url(newUrl).build())
            }.build()

    private val gson = GsonBuilder()
            .registerTypeAdapter(SubsonicResponse::class.javaObjectType,
                    SubsonicResponse.Companion.ClassTypeAdapter())
            .create()

    private val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    private val subsonicAPI = retrofit.create(SubsonicAPIDefinition::class.java)

    /**
     * Get API instance.
     *
     * @return initialized API instance
     */
    fun getApi() = subsonicAPI

    private fun passwordHex() = "enc:${password.toHexBytes()}"

    private fun String.toHexBytes(): String {
        return String.format("%040x", BigInteger(1, this.toByteArray()))
    }
}