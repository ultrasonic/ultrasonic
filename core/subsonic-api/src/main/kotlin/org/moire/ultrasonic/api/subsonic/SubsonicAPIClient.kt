package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.moire.ultrasonic.api.subsonic.interceptors.PasswordHexInterceptor
import org.moire.ultrasonic.api.subsonic.interceptors.PasswordMD5Interceptor
import org.moire.ultrasonic.api.subsonic.interceptors.ProxyPasswordInterceptor
import org.moire.ultrasonic.api.subsonic.interceptors.RangeHeaderInterceptor
import org.moire.ultrasonic.api.subsonic.interceptors.VersionInterceptor
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Response
import retrofit2.Retrofit

private const val READ_TIMEOUT = 60_000L

/**
 * Subsonic API client that provides api access.
 *
 * For supported API calls see [SubsonicAPIDefinition].
 *
 * Client will automatically adjust [protocolVersion] to the current server version on
 * doing successful requests.
 *
 * @author Yahor Berdnikau
 */
class SubsonicAPIClient(
    config: SubsonicClientConfiguration,
    private val okLogger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    baseOkClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val versionInterceptor = VersionInterceptor(config.minimalProtocolVersion)

    private val proxyPasswordInterceptor = ProxyPasswordInterceptor(
        config.minimalProtocolVersion,
        PasswordHexInterceptor(config.password),
        PasswordMD5Interceptor(config.password),
        config.enableLdapUserSupport
    )

    /**
     * Get currently used protocol version.
     */
    var protocolVersion = config.minimalProtocolVersion
        private set(value) {
            field = value
            proxyPasswordInterceptor.apiVersion = field
            wrappedApi.currentApiVersion = field
            versionInterceptor.protocolVersion = field
        }

    private val okHttpClient = baseOkClient.newBuilder()
        .readTimeout(READ_TIMEOUT, MILLISECONDS)
        .apply { if (config.allowSelfSignedCertificate) allowSelfSignedCertificates() }
        .addInterceptor { chain ->
            // Adds default request params
            val originalRequest = chain.request()
            val newUrl = originalRequest.url().newBuilder()
                .addQueryParameter("u", config.username)
                .addQueryParameter("c", config.clientID)
                .addQueryParameter("f", "json")
                .build()
            chain.proceed(originalRequest.newBuilder().url(newUrl).build())
        }
        .addInterceptor(versionInterceptor)
        .addInterceptor(proxyPasswordInterceptor)
        .addInterceptor(RangeHeaderInterceptor())
        .apply { if (config.debug) addLogging() }
        .build()

    private val jacksonMapper = ObjectMapper()
        .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .registerModule(KotlinModule())

    private val retrofit = Retrofit.Builder()
        .baseUrl("${config.baseUrl}/rest/")
        .client(okHttpClient)
        .addConverterFactory(
            VersionAwareJacksonConverterFactory.create(
                { protocolVersion = it },
                jacksonMapper
            )
        )
        .build()

    private val wrappedApi = ApiVersionCheckWrapper(
        retrofit.create(SubsonicAPIDefinition::class.java),
        config.minimalProtocolVersion
    )

    val api: SubsonicAPIDefinition get() = wrappedApi

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
            val contentType = responseBody?.contentType()
            if (
                contentType != null &&
                contentType.type().equals("application", true) &&
                contentType.subtype().equals("json", true)
            ) {
                val error = jacksonMapper.readValue<SubsonicResponse>(responseBody.byteStream())
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
     * Get stream url.
     *
     * Calling this method do actual connection to the backend, though not downloading all content.
     *
     * Consider do not use this method, but [stream] call.
     */
    fun getStreamUrl(id: String): String {
        val request = api.stream(id).execute()
        val url = request.raw().request().url().toString()
        if (request.isSuccessful) {
            request.body()?.close()
        }
        return url
    }

    private fun OkHttpClient.Builder.addLogging() {
        val loggingInterceptor = HttpLoggingInterceptor(okLogger)
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        this.addInterceptor(loggingInterceptor)
    }

    @SuppressWarnings("TrustAllX509TrustManager", "EmptyFunctionBlock")
    private fun OkHttpClient.Builder.allowSelfSignedCertificates() {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())

        sslSocketFactory(sslContext.socketFactory, trustManager)

        hostnameVerifier { _, _ -> true }
    }
}
