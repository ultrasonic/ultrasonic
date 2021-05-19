package org.moire.ultrasonic.api.subsonic

import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_11_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_12_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_14_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_2_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_3_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_4_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_5_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_6_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_7_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_8_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_9_0
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
import org.moire.ultrasonic.api.subsonic.response.GetLyricsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPodcastsResponse
import org.moire.ultrasonic.api.subsonic.response.GetRandomSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetSongsByGenreResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredTwoResponse
import org.moire.ultrasonic.api.subsonic.response.GetUserResponse
import org.moire.ultrasonic.api.subsonic.response.JukeboxResponse
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse
import org.moire.ultrasonic.api.subsonic.response.SharesResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.response.VideosResponse
import retrofit2.Call

/**
 * Special wrapper for [SubsonicAPIDefinition] that checks if [currentApiVersion] is suitable
 * for this call.
 */
@Suppress("TooManyFunctions")
internal class ApiVersionCheckWrapper(
    val api: SubsonicAPIDefinition,
    var currentApiVersion: SubsonicAPIVersions
) : SubsonicAPIDefinition by api {
    override fun getArtists(musicFolderId: String?): Call<GetArtistsResponse> {
        checkVersion(V1_8_0)
        return api.getArtists(musicFolderId)
    }

    override fun star(id: String?, albumId: String?, artistId: String?): Call<SubsonicResponse> {
        checkVersion(V1_8_0)
        return api.star(id, albumId, artistId)
    }

    override fun unstar(id: String?, albumId: String?, artistId: String?): Call<SubsonicResponse> {
        checkVersion(V1_8_0)
        return api.unstar(id, albumId, artistId)
    }

    override fun getArtist(id: String): Call<GetArtistResponse> {
        checkVersion(V1_8_0)
        return api.getArtist(id)
    }

    override fun getAlbum(id: String): Call<GetAlbumResponse> {
        checkVersion(V1_8_0)
        return api.getAlbum(id)
    }

    override fun search2(
        query: String,
        artistCount: Int?,
        artistOffset: Int?,
        albumCount: Int?,
        albumOffset: Int?,
        songCount: Int?,
        musicFolderId: String?
    ): Call<SearchTwoResponse> {
        checkVersion(V1_4_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.search2(
            query, artistCount, artistOffset, albumCount, albumOffset, songCount, musicFolderId
        )
    }

    override fun search3(
        query: String,
        artistCount: Int?,
        artistOffset: Int?,
        albumCount: Int?,
        albumOffset: Int?,
        songCount: Int?,
        musicFolderId: String?
    ): Call<SearchThreeResponse> {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.search3(
            query, artistCount, artistOffset, albumCount, albumOffset, songCount, musicFolderId
        )
    }

    override fun getPlaylists(username: String?): Call<GetPlaylistsResponse> {
        checkParamVersion(username, V1_8_0)
        return api.getPlaylists(username)
    }

    override fun createPlaylist(
        id: String?,
        name: String?,
        songIds: List<String>?
    ): Call<SubsonicResponse> {
        checkVersion(V1_2_0)
        return api.createPlaylist(id, name, songIds)
    }

    override fun deletePlaylist(id: String): Call<SubsonicResponse> {
        checkVersion(V1_2_0)
        return api.deletePlaylist(id)
    }

    override fun updatePlaylist(
        id: String,
        name: String?,
        comment: String?,
        public: Boolean?,
        songIdsToAdd: List<String>?,
        songIndexesToRemove: List<Int>?
    ): Call<SubsonicResponse> {
        checkVersion(V1_8_0)
        return api.updatePlaylist(id, name, comment, public, songIdsToAdd, songIndexesToRemove)
    }

    override fun getPodcasts(includeEpisodes: Boolean?, id: String?): Call<GetPodcastsResponse> {
        checkVersion(V1_6_0)
        checkParamVersion(includeEpisodes, V1_9_0)
        checkParamVersion(id, V1_9_0)
        return api.getPodcasts(includeEpisodes, id)
    }

    override fun getLyrics(artist: String?, title: String?): Call<GetLyricsResponse> {
        checkVersion(V1_2_0)
        return api.getLyrics(artist, title)
    }

    override fun scrobble(id: String, time: Long?, submission: Boolean?): Call<SubsonicResponse> {
        checkVersion(V1_5_0)
        checkParamVersion(time, V1_8_0)
        return api.scrobble(id, time, submission)
    }

    override fun getAlbumList(
        type: AlbumListType,
        size: Int?,
        offset: Int?,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
        musicFolderId: String?
    ): Call<GetAlbumListResponse> {
        checkVersion(V1_2_0)
        checkParamVersion(musicFolderId, V1_11_0)
        return api.getAlbumList(type, size, offset, fromYear, toYear, genre, musicFolderId)
    }

    override fun getAlbumList2(
        type: AlbumListType,
        size: Int?,
        offset: Int?,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
        musicFolderId: String?
    ): Call<GetAlbumList2Response> {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getAlbumList2(type, size, offset, fromYear, toYear, genre, musicFolderId)
    }

    override fun getRandomSongs(
        size: Int?,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
        musicFolderId: String?
    ): Call<GetRandomSongsResponse> {
        checkVersion(V1_2_0)
        return api.getRandomSongs(size, genre, fromYear, toYear, musicFolderId)
    }

    override fun getStarred(musicFolderId: String?): Call<GetStarredResponse> {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getStarred(musicFolderId)
    }

    override fun getStarred2(musicFolderId: String?): Call<GetStarredTwoResponse> {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getStarred2(musicFolderId)
    }

    override fun stream(
        id: String,
        maxBitRate: Int?,
        format: String?,
        timeOffset: Int?,
        videoSize: String?,
        estimateContentLength: Boolean?,
        converted: Boolean?,
        offset: Long?
    ): Call<ResponseBody> {
        checkParamVersion(maxBitRate, V1_2_0)
        checkParamVersion(format, V1_6_0)
        checkParamVersion(videoSize, V1_6_0)
        checkParamVersion(estimateContentLength, V1_8_0)
        checkParamVersion(converted, V1_14_0)
        return api.stream(
            id, maxBitRate, format, timeOffset, videoSize, estimateContentLength, converted
        )
    }

    override fun jukeboxControl(
        action: JukeboxAction,
        index: Int?,
        offset: Int?,
        ids: List<String>?,
        gain: Float?
    ): Call<JukeboxResponse> {
        checkVersion(V1_2_0)
        checkParamVersion(offset, V1_7_0)
        return api.jukeboxControl(action, index, offset, ids, gain)
    }

    override fun getShares(): Call<SharesResponse> {
        checkVersion(V1_6_0)
        return api.getShares()
    }

    override fun createShare(
        idsToShare: List<String>,
        description: String?,
        expires: Long?
    ): Call<SharesResponse> {
        checkVersion(V1_6_0)
        return api.createShare(idsToShare, description, expires)
    }

    override fun deleteShare(id: String): Call<SubsonicResponse> {
        checkVersion(V1_6_0)
        return api.deleteShare(id)
    }

    override fun updateShare(
        id: String,
        description: String?,
        expires: Long?
    ): Call<SubsonicResponse> {
        checkVersion(V1_6_0)
        return api.updateShare(id, description, expires)
    }

    override fun getGenres(): Call<GenresResponse> {
        checkVersion(V1_9_0)
        return api.getGenres()
    }

    override fun getSongsByGenre(
        genre: String,
        count: Int,
        offset: Int,
        musicFolderId: String?
    ): Call<GetSongsByGenreResponse> {
        checkVersion(V1_9_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getSongsByGenre(genre, count, offset, musicFolderId)
    }

    override fun getUser(username: String): Call<GetUserResponse> {
        checkVersion(V1_3_0)
        return api.getUser(username)
    }

    override fun getChatMessages(since: Long?): Call<ChatMessagesResponse> {
        checkVersion(V1_2_0)
        return api.getChatMessages(since)
    }

    override fun addChatMessage(message: String): Call<SubsonicResponse> {
        checkVersion(V1_2_0)
        return api.addChatMessage(message)
    }

    override fun getBookmarks(): Call<BookmarksResponse> {
        checkVersion(V1_9_0)
        return api.getBookmarks()
    }

    override fun createBookmark(
        id: String,
        position: Long,
        comment: String?
    ): Call<SubsonicResponse> {
        checkVersion(V1_9_0)
        return api.createBookmark(id, position, comment)
    }

    override fun deleteBookmark(id: String): Call<SubsonicResponse> {
        checkVersion(V1_9_0)
        return api.deleteBookmark(id)
    }

    override fun getVideos(): Call<VideosResponse> {
        checkVersion(V1_8_0)
        return api.getVideos()
    }

    override fun getAvatar(username: String): Call<ResponseBody> {
        checkVersion(V1_8_0)
        return api.getAvatar(username)
    }

    private fun checkVersion(expectedVersion: SubsonicAPIVersions) {
        if (currentApiVersion < expectedVersion) throw ApiNotSupportedException(currentApiVersion)
    }

    private fun checkParamVersion(param: Any?, expectedVersion: SubsonicAPIVersions) {
        if (param != null) {
            checkVersion(expectedVersion)
        }
    }
}
