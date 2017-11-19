package org.moire.ultrasonic.api.subsonic

import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.response.BookmarksResponse
import org.moire.ultrasonic.api.subsonic.response.ChatMessagesResponse
import org.moire.ultrasonic.api.subsonic.response.GenresResponse
import org.moire.ultrasonic.api.subsonic.response.GetAlbumList2Response
import org.moire.ultrasonic.api.subsonic.response.GetAlbumListResponse
import org.moire.ultrasonic.api.subsonic.response.GetAlbumResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse
import org.moire.ultrasonic.api.subsonic.response.GetLyricsResponse
import org.moire.ultrasonic.api.subsonic.response.GetMusicDirectoryResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPodcastsResponse
import org.moire.ultrasonic.api.subsonic.response.GetRandomSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetSongsByGenreResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredTwoResponse
import org.moire.ultrasonic.api.subsonic.response.GetUserResponse
import org.moire.ultrasonic.api.subsonic.response.JukeboxResponse
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse
import org.moire.ultrasonic.api.subsonic.response.SearchResponse
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse
import org.moire.ultrasonic.api.subsonic.response.SharesResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.response.VideosResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    @GET("getPlaylists.view")
    fun getPlaylists(@Query("username") username: String? = null): Call<GetPlaylistsResponse>

    @GET("createPlaylist.view")
    fun createPlaylist(@Query("playlistId") id: Long? = null,
                       @Query("name") name: String? = null,
                       @Query("songId") songIds: List<Long>? = null): Call<SubsonicResponse>

    @GET("deletePlaylist.view")
    fun deletePlaylist(@Query("id") id: Long): Call<SubsonicResponse>

    @GET("updatePlaylist.view")
    fun updatePlaylist(
            @Query("playlistId") id: Long,
            @Query("name") name: String? = null,
            @Query("comment") comment: String? = null,
            @Query("public") public: Boolean? = null,
            @Query("songIdToAdd") songIdsToAdd: List<Long>? = null,
            @Query("songIndexToRemove") songIndexesToRemove: List<Int>? = null): Call<SubsonicResponse>

    @GET("getPodcasts.view")
    fun getPodcasts(@Query("includeEpisodes") includeEpisodes: Boolean? = null,
                    @Query("id") id: Long? = null): Call<GetPodcastsResponse>

    @GET("getLyrics.view")
    fun getLyrics(@Query("artist") artist: String? = null,
                  @Query("title") title: String? = null): Call<GetLyricsResponse>

    @GET("scrobble.view")
    fun scrobble(@Query("id") id: String,
                 @Query("time") time: Long? = null,
                 @Query("submission") submission: Boolean? = null): Call<SubsonicResponse>

    @GET("getAlbumList.view")
    fun getAlbumList(@Query("type") type: AlbumListType,
                     @Query("size") size: Int? = null,
                     @Query("offset") offset: Int? = null,
                     @Query("fromYear") fromYear: Int? = null,
                     @Query("toYear") toYear: Int? = null,
                     @Query("genre") genre: String? = null,
                     @Query("musicFolderId") musicFolderId: Long? = null): Call<GetAlbumListResponse>

    @GET("getAlbumList2.view")
    fun getAlbumList2(@Query("type") type: AlbumListType,
                      @Query("size") size: Int? = null,
                      @Query("offset") offset: Int? = null,
                      @Query("fromYear") fromYear: Int? = null,
                      @Query("toYear") toYear: Int? = null,
                      @Query("genre") genre: String? = null,
                      @Query("musicFolderId") musicFolderId: Long? = null): Call<GetAlbumList2Response>

    @GET("getRandomSongs.view")
    fun getRandomSongs(@Query("size") size: Int? = null,
                       @Query("genre") genre: String? = null,
                       @Query("fromYear") fromYear: Int? = null,
                       @Query("toYear") toYear: Int? = null,
                       @Query("musicFolderId") musicFolderId: Long? = null): Call<GetRandomSongsResponse>

    @GET("getStarred.view")
    fun getStarred(@Query("musicFolderId") musicFolderId: Long? = null): Call<GetStarredResponse>

    @GET("getStarred2.view")
    fun getStarred2(@Query("musicFolderId") musicFolderId: Long? = null): Call<GetStarredTwoResponse>

    @Streaming
    @GET("getCoverArt.view")
    fun getCoverArt(@Query("id") id: String,
                    @Query("size") size: Long? = null): Call<ResponseBody>

    @Streaming
    @GET("stream.view")
    fun stream(@Query("id") id: String,
               @Query("maxBitRate") maxBitRate: Int? = null,
               @Query("format") format: String? = null,
               @Query("timeOffset") timeOffset: Int? = null,
               @Query("size") videoSize: String? = null,
               @Query("estimateContentLength") estimateContentLength: Boolean? = null,
               @Query("converted") converted: Boolean? = null,
               @Header("Range") offset: Long? = null): Call<ResponseBody>

    @GET("jukeboxControl.view")
    fun jukeboxControl(@Query("action") action: JukeboxAction,
                       @Query("index") index: Int? = null,
                       @Query("offset") offset: Int? = null,
                       @Query("id") ids: List<String>? = null,
                       @Query("gain") gain: Float? = null): Call<JukeboxResponse>

    @GET("getShares.view")
    fun getShares(): Call<SharesResponse>

    @GET("createShare.view")
    fun createShare(@Query("id") idsToShare: List<String>,
                    @Query("description") description: String? = null,
                    @Query("expires") expires: Long? = null): Call<SharesResponse>

    @GET("deleteShare.view")
    fun deleteShare(@Query("id") id: Long): Call<SubsonicResponse>

    @GET("updateShare.view")
    fun updateShare(@Query("id") id: Long,
                    @Query("description") description: String? = null,
                    @Query("expires") expires: Long? = null): Call<SubsonicResponse>

    @GET("getGenres.view")
    fun getGenres(): Call<GenresResponse>

    @GET("getSongsByGenre.view")
    fun getSongsByGenre(
            @Query("genre") genre: String,
            @Query("count") count: Int = 10,
            @Query("offset") offset: Int = 0,
            @Query("musicFolderId") musicFolderId: Long? = null): Call<GetSongsByGenreResponse>

    @GET("getUser.view")
    fun getUser(@Query("username") username: String): Call<GetUserResponse>

    @GET("getChatMessages.view")
    fun getChatMessages(@Query("since") since: Long? = null): Call<ChatMessagesResponse>

    @GET("addChatMessage.view")
    fun addChatMessage(@Query("message") message: String): Call<SubsonicResponse>

    @GET("getBookmarks.view")
    fun getBookmarks(): Call<BookmarksResponse>

    @GET("createBookmark.view")
    fun createBookmark(
            @Query("id") id: Int,
            @Query("position") position: Long,
            @Query("comment") comment: String? = null): Call<SubsonicResponse>

    @GET("deleteBookmark.view")
    fun deleteBookmark(@Query("id") id: Int): Call<SubsonicResponse>

    @GET("getVideos.view")
    fun getVideos(): Call<VideosResponse>
}
