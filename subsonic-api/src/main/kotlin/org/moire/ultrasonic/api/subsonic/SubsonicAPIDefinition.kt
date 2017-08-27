package org.moire.ultrasonic.api.subsonic

import org.moire.ultrasonic.api.subsonic.response.GetAlbumResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse
import org.moire.ultrasonic.api.subsonic.response.GetMusicDirectoryResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistResponse
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse
import org.moire.ultrasonic.api.subsonic.response.SearchResponse
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subsonic API calls.
 *
 * For methods description see [http://www.subsonic.org/pages/api.jsp].
 */
@Suppress("TooManyFunctions", "LongParameterList")
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

    @GET("getMusicDirectory.view")
    fun getMusicDirectory(@Query("id") id: Long): Call<GetMusicDirectoryResponse>

    @GET("getArtists.view")
    fun getArtists(@Query("musicFolderId") musicFolderId: Long?): Call<GetArtistsResponse>

    @GET("star.view")
    fun star(@Query("id") id: Long? = null,
             @Query("albumId") albumId: Long? = null,
             @Query("artistId") artistId: Long? = null): Call<SubsonicResponse>

    @GET("unstar.view")
    fun unstar(@Query("id") id: Long? = null,
               @Query("albumId") albumId: Long? = null,
               @Query("artistId") artistId: Long? = null): Call<SubsonicResponse>

    @GET("getArtist.view")
    fun getArtist(@Query("id") id: Long): Call<GetArtistResponse>

    @GET("getAlbum.view")
    fun getAlbum(@Query("id") id: Long): Call<GetAlbumResponse>

    @GET("search.view")
    fun search(@Query("artist") artist: String? = null,
               @Query("album") album: String? = null,
               @Query("title") title: String? = null,
               @Query("any") any: String? = null,
               @Query("count") count: Int? = null,
               @Query("offset") offset: Int? = null,
               @Query("newerThan") newerThan: Long? = null): Call<SearchResponse>

    @GET("search2.view")
    fun search2(@Query("query") query: String,
                @Query("artistCount") artistCount: Int? = null,
                @Query("artistOffset") artistOffset: Int? = null,
                @Query("albumCount") albumCount: Int? = null,
                @Query("albumOffset") albumOffset: Int? = null,
                @Query("songCount") songCount: Int? = null,
                @Query("musicFolderId") musicFolderId: Long? = null): Call<SearchTwoResponse>

    @GET("search3.view")
    fun search3(@Query("query") query: String,
                @Query("artistCount") artistCount: Int? = null,
                @Query("artistOffset") artistOffset: Int? = null,
                @Query("albumCount") albumCount: Int? = null,
                @Query("albumOffset") albumOffset: Int? = null,
                @Query("songCount") songCount: Int? = null,
                @Query("musicFolderId") musicFolderId: Long? = null): Call<SearchThreeResponse>

    @GET("getPlaylist.view")
    fun getPlaylist(@Query("id") id: Long): Call<GetPlaylistResponse>
}
