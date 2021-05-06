package org.moire.ultrasonic.service

import java.io.IOException
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.data.ActiveServerProvider
import retrofit2.Response
import timber.log.Timber

/**
 * This call wraps Subsonic API calls so their results can be checked for errors, API version, etc
 */
class ApiCallResponseChecker(
    private val subsonicAPIClient: SubsonicAPIClient,
    private val activeServerProvider: ActiveServerProvider
) {
    /**
     * Executes a Subsonic API call with response check
     */
    @Throws(SubsonicRESTException::class, IOException::class)
    fun <T : Response<out SubsonicResponse>> callWithResponseCheck(
        call: (SubsonicAPIDefinition) -> T
    ): T {
        // Check for API version when first contacting the server
        if (activeServerProvider.getActiveServer().minimumApiVersion == null) {
            try {
                val response = subsonicAPIClient.api.ping().execute()
                if (response.body() != null) {
                    val restApiVersion = response.body()!!.version.restApiVersion
                    Timber.i("Server minimum API version set to %s", restApiVersion)
                    activeServerProvider.setMinimumApiVersion(restApiVersion)
                }
            } catch (ignored: Exception) {
                // This Ping is only used to get the API Version, if it fails, that's no problem.
            }
        }

        // This call will be now executed with the correct API Version, so it shouldn't fail
        val result = call.invoke(subsonicAPIClient.api)
        checkResponseSuccessful(result)
        return result
    }

    /**
     * Creates Exceptions from the results returned by the Subsonic API
     */
    companion object {
        @Throws(SubsonicRESTException::class, IOException::class)
        fun checkResponseSuccessful(response: Response<out SubsonicResponse>) {
            if (response.isSuccessful && response.body()!!.status === SubsonicResponse.Status.OK) {
                return
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
    }
}
