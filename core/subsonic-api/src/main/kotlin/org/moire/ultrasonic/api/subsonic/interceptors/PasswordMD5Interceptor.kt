package org.moire.ultrasonic.api.subsonic.interceptors

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Locale
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
    private val secureRandom = SecureRandom()
    private val saltBytes = ByteArray(16)

    override fun intercept(chain: Chain): Response {
        val originalRequest = chain.request()
        val salt = getSalt()
        val updatedUrl = originalRequest.url().newBuilder()
            .addQueryParameter("t", getPasswordMD5Hash(salt))
            .addQueryParameter("s", salt)
            .build()

        return chain.proceed(originalRequest.newBuilder().url(updatedUrl).build())
    }

    private fun getSalt(): String {
        secureRandom.nextBytes(saltBytes)
        return saltBytes.toHexBytes()
    }

    private fun getPasswordMD5Hash(salt: String): String {
        try {
            val md5Digest = MessageDigest.getInstance("MD5")
            return md5Digest.digest(
                "$password$salt".toByteArray()
            ).toHexBytes().lowercase(Locale.getDefault())
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }
}
