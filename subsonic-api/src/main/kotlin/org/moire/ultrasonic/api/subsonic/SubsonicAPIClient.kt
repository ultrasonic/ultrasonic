package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.lang.IllegalStateException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Subsonic API client that provides api access.
 *
 * For supported API calls see [SubsonicAPIDefinition].
 *
 * @author Yahor Berdnikau
 */
class SubsonicAPIClient(baseUrl: String,
                        username: String,
                        private val password: String,
                        clientProtocolVersion: SubsonicAPIVersions,
                        clientID: String,
                        debug: Boolean = false) {
    companion object {
        internal val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    }

    private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Adds default request params
                val originalRequest = chain.request()
                val newUrl = originalRequest.url().newBuilder()
                        .addQueryParameter("u", username)
                        .also {
                            it.addPasswordQueryParam(clientProtocolVersion)
                        }
                        .addQueryParameter("v", clientProtocolVersion.restApiVersion)
                        .addQueryParameter("c", clientID)
                        .addQueryParameter("f", "json")
                        .build()
                chain.proceed(originalRequest.newBuilder().url(newUrl).build())
            }
            .also {
                if (debug) {
                    it.addLogging()
                }
            }.build()

    private val jacksonMapper = ObjectMapper()
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
            .registerModule(KotlinModule())

    private val retrofit = Retrofit.Builder()
            .baseUrl("$baseUrl/rest/")
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(jacksonMapper))
            .build()

    val api: SubsonicAPIDefinition = retrofit.create(SubsonicAPIDefinition::class.java)

    private val salt: String by lazy {
        val secureRandom = SecureRandom()
        BigInteger(130, secureRandom).toString(32)
    }

    private val passwordMD5Hash: String by lazy {
        try {
            val md5Digest = MessageDigest.getInstance("MD5")
            md5Digest.digest("$password$salt".toByteArray()).toHexBytes()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }

    private val passwordHex: String by lazy {
        "enc:${password.toHexBytes()}"
    }

    private fun String.toHexBytes(): String {
        return this.toByteArray().toHexBytes()
    }

    private fun ByteArray.toHexBytes(): String {
        val hexChars = CharArray(this.size * 2)
        for (j in 0..this.lastIndex) {
            val v = this[j].toInt().and(0xFF)
            hexChars[j * 2] = HEX_ARRAY[v.ushr(4)]
            hexChars[j * 2 + 1] = HEX_ARRAY[v.and(0x0F)]
        }
        return String(hexChars)
    }

    private fun OkHttpClient.Builder.addLogging() {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC
        this.addInterceptor(loggingInterceptor)
    }

    private fun HttpUrl.Builder.addPasswordQueryParam(clientProtocolVersion: SubsonicAPIVersions) {
        if (clientProtocolVersion < SubsonicAPIVersions.V1_13_0) {
            this.addQueryParameter("p", passwordHex)
        } else {
            this.addQueryParameter("t", passwordMD5Hash)
            this.addQueryParameter("s", salt)
        }
    }
}
