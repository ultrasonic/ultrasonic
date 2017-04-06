package org.moire.ultrasonic.api.subsonic

import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subsonic API calls.
 *
 * For methods description see [http://www.subsonic.org/pages/api.jsp].
 */
interface SubsonicAPIDefinition {
    @GET("ping.view")
    fun ping(): Call<SubsonicResponse>

    @GET("getLicense.view")
    fun getLicense(): Call<LicenseResponse>

    @GET("getMusicFolders.view")
    fun getMusicFolders(): Call<MusicFoldersResponse>

    @GET("getIndexes.view")
    fun getIndexes(@Query("musicFolderId") musicFolderId: Long?,
                   @Query("ifModifiedSince") ifModifiedSince: Long?): Call<GetIndexesResponse>
}