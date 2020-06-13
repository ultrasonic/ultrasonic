package org.moire.ultrasonic.api.subsonic.interceptors

import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

/**
 * Adds password param as MD5 hash with random salt. Salt is also added as a param.
 *
 * Should be enabled for requests against [org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_13_0]
 * and above.
 */
class PasswordMD5Interceptor(private val password: String) : Interceptor {
    private val salt: String by lazy {
        val secureRandom = SecureRandom()
        BigInteger(130, secureRandom).toString(32)
    }

    private val passwordMD5Hash: String by lazy {
        try {
            val md5Digest = MessageDigest.getInstance("MD5")
            md5Digest.digest("$password$salt".toByteArray()).toHexBytes().toLowerCase()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }

    override fun intercept(chain: Chain): Response {
        val originalRequest = chain.request()
        val updatedUrl = originalRequest.url().newBuilder()
            .addQueryParameter("t", passwordMD5Hash)
            .addQueryParameter("s", salt)
            .build()

        return chain.proceed(originalRequest.newBuilder().url(updatedUrl).build())
    }
}
