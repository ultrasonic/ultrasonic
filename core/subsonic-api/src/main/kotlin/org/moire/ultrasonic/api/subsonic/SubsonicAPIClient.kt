package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
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

    var onProtocolChange: (SubsonicAPIVersions) -> Unit = {}

    /**
     * The currently used protocol version.
     * The setter also updates the interceptors and callback (if registered)
     */
    var protocolVersion = config.minimalProtocolVersion
        private set(value) {
            field = value
            proxyPasswordInterceptor.apiVersion = field
            wrappedApi.currentApiVersion = field
            wrappedApi.isRealProtocolVersion = true
            versionInterceptor.protocolVersion = field
            onProtocolChange(field)
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

    // Create the Retrofit instance, and register a special converter factory
    // It will update our protocol version to the correct version, once we made a successful call
    private val retrofit = Retrofit.Builder()
        .baseUrl("${config.baseUrl}/rest/")
        .client(okHttpClient)
        .addConverterFactory(
            VersionAwareJacksonConverterFactory.create(
                {
                    // Only trigger update on change, or if still using the default
                    if (protocolVersion != it || !config.isRealProtocolVersion) {
                        protocolVersion = it
                    }
                },
                jacksonMapper
            )
        )
        .build()

    private val wrappedApi = ApiVersionCheckWrapper(
        retrofit.create(SubsonicAPIDefinition::class.java),
        config.minimalProtocolVersion,
        config.isRealProtocolVersion
    )

    val api: SubsonicAPIDefinition get() = wrappedApi

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

    /**
     * This function is necessary because Mockito has problems with stubbing chained calls
     */
    fun toStreamResponse(call: Response<ResponseBody>): StreamResponse {
        return call.toStreamResponse()
    }

    companion object {
        val jacksonMapper: ObjectMapper = ObjectMapper()
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .registerModule(KotlinModule())
    }
}
