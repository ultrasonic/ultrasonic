package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.moire.ultrasonic.api.subsonic.interceptors.RangeHeaderInterceptor
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.lang.IllegalStateException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val READ_TIMEOUT = 60_000L

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
            .readTimeout(READ_TIMEOUT, MILLISECONDS)
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
            .addInterceptor(RangeHeaderInterceptor())
            .also {
                if (debug) {
                    it.addLogging()
                }
            }.build()

    private val jacksonMapper = ObjectMapper()
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(KotlinModule())

    private val retrofit = Retrofit.Builder()
            .baseUrl("$baseUrl/rest/")
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(jacksonMapper))
            .build()

    val api: SubsonicAPIDefinition = retrofit.create(SubsonicAPIDefinition::class.java)

    /**
     * Convenient method to get cover art from api using item [id] and optional maximum [size].
     *
     * It detects the response `Content-Type` and tries to parse subsonic error if there is one.
     *
     * Prefer this method over [SubsonicAPIDefinition.getCoverArt] as this handles error cases.
     */
    fun getCoverArt(id: String, size: Long? = null): StreamResponse = handleStreamResponse {
        api.getCoverArt(id, size).execute()
    }

    /**
     * Convenient method to get media stream from api using item [id] and optional [maxBitrate].
     *
     * Optionally also you can provide [offset] that stream should start from.
     *
     * It detects the response `Content-Type` and tries to parse subsonic error if there is one.
     *
     * Prefer this method over [SubsonicAPIDefinition.stream] as this handles error cases.
     */
    fun stream(id: String, maxBitrate: Int? = null, offset: Long? = null): StreamResponse =
            handleStreamResponse {
                api.stream(id, maxBitrate, offset = offset).execute()
            }

    /**
     * Convenient method to get user avatar using [username].
     *
     * It detects the response `Content-Type` and tries to parse subsonic error if there is one.
     *
     * Prefer this method over [SubsonicAPIDefinition.getAvatar] as this handles error cases.
     */
    fun getAvatar(username: String): StreamResponse = handleStreamResponse {
        api.getAvatar(username).execute()
    }

    private inline fun handleStreamResponse(apiCall: () -> Response<ResponseBody>): StreamResponse {
        val response = apiCall()
        return if (response.isSuccessful) {
            val responseBody = response.body()
            val contentType = responseBody.contentType()
            if (contentType != null &&
                    contentType.type().equals("application", true) &&
                    contentType.subtype().equals("json", true)) {
                val error = jacksonMapper.readValue<SubsonicResponse>(responseBody.byteStream())
                StreamResponse(apiError = error.error, responseHttpCode = response.code())
            } else {
                StreamResponse(stream = responseBody.byteStream(),
                        responseHttpCode = response.code())
            }
        } else {
            StreamResponse(responseHttpCode = response.code())
        }
    }

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
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
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
