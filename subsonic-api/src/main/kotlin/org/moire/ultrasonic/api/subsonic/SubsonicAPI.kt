package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
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
                  clientID: String,
                  debug: Boolean = false) {
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
            }
            .apply {
                if (debug) {
                    val loggingInterceptor = HttpLoggingInterceptor()
                    loggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC
                    this.addInterceptor(loggingInterceptor)
                }
            }.build()

    private val jacksonMapper = ObjectMapper()
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
            .registerModule(KotlinModule())

    private val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(jacksonMapper))
            .build()

    private val subsonicAPI = retrofit.create(SubsonicAPIDefinition::class.java)

    /**
     * Get API instance.
     *
     * @return initialized API instance
     */
    fun getApi(): SubsonicAPIDefinition = subsonicAPI

    private fun passwordHex() = "enc:${password.toHexBytes()}"

    private fun String.toHexBytes(): String {
        return String.format("%040x", BigInteger(1, this.toByteArray()))
    }
}