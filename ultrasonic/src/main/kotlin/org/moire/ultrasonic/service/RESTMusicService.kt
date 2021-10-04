/*
 * RestMusicService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import java.io.IOException
import java.io.InputStream
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.Companion.fromName
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.ChatMessage
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.PodcastsChannel
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.domain.UserInfo
import org.moire.ultrasonic.domain.toArtistList
import org.moire.ultrasonic.domain.toDomainEntitiesList
import org.moire.ultrasonic.domain.toDomainEntity
import org.moire.ultrasonic.domain.toDomainEntityList
import org.moire.ultrasonic.domain.toIndexList
import org.moire.ultrasonic.domain.toMusicDirectoryDomainEntity
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * This Music Service implementation connects to a server using the Subsonic REST API
 */
@Suppress("LargeClass")
open class RESTMusicService(
    val subsonicAPIClient: SubsonicAPIClient,
    private val activeServerProvider: ActiveServerProvider
) : MusicService {

    // Shortcut to the API
    @Suppress("VariableNaming", "PropertyName")
    val API = subsonicAPIClient.api

    @Throws(Exception::class)
    override fun ping() {
        API.ping().execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun isLicenseValid(): Boolean {
        val response = API.getLicense().execute().throwOnFailure()

        return response.body()!!.license.valid
    }

    @Throws(Exception::class)
    override fun getMusicFolders(
        refresh: Boolean
    ): List<MusicFolder> {
        val response = API.getMusicFolders().execute().throwOnFailure()

        return response.body()!!.musicFolders.toDomainEntityList()
    }

    /**
     *  Retrieves the artists for a given music folder     *
     */
    @Throws(Exception::class)
    override fun getIndexes(
        musicFolderId: String?,
        refresh: Boolean
    ): List<Index> {
        val response = API.getIndexes(musicFolderId, null).execute().throwOnFailure()

        return response.body()!!.indexes.toIndexList(musicFolderId)
    }

    @Throws(Exception::class)
    override fun getArtists(
        refresh: Boolean
    ): List<Artist> {
        val response = API.getArtists(null).execute().throwOnFailure()

        return response.body()!!.indexes.toArtistList()
    }

    @Throws(Exception::class)
    override fun star(
        id: String?,
        albumId: String?,
        artistId: String?
    ) {
        API.star(id, albumId, artistId).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun unstar(
        id: String?,
        albumId: String?,
        artistId: String?
    ) {
        API.unstar(id, albumId, artistId).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun setRating(
        id: String,
        rating: Int
    ) {
        API.setRating(id, rating).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getMusicDirectory(id).execute().throwOnFailure()

        return response.body()!!.musicDirectory.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getArtist(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getArtist(id).execute().throwOnFailure()

        return response.body()!!.artist.toMusicDirectoryDomainEntity()
    }

    @Throws(Exception::class)
    override fun getAlbum(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getAlbum(id).execute().throwOnFailure()

        return response.body()!!.album.toMusicDirectoryDomainEntity()
    }

    @Throws(Exception::class)
    override fun search(
        criteria: SearchCriteria
    ): SearchResult {
        return try {
            if (!isOffline() && Settings.shouldUseId3Tags) {
                search3(criteria)
            } else {
                search2(criteria)
            }
        } catch (ignored: ApiNotSupportedException) {
            // Ensure backward compatibility with REST 1.3.
            searchOld(criteria)
        }
    }

    /**
     * Search using the "search" REST method.
     */
    @Throws(Exception::class)
    private fun searchOld(
        criteria: SearchCriteria
    ): SearchResult {
        val response =
            API.search(null, null, null, criteria.query, criteria.songCount, null, null)
                .execute().throwOnFailure()

        return response.body()!!.searchResult.toDomainEntity()
    }

    /**
     * Search using the "search2" REST method, available in 1.4.0 and later.
     */
    @Throws(Exception::class)
    private fun search2(
        criteria: SearchCriteria
    ): SearchResult {
        requireNotNull(criteria.query) { "Query param is null" }
        val response = API.search2(
            criteria.query, criteria.artistCount, null, criteria.albumCount, null,
            criteria.songCount, null
        ).execute().throwOnFailure()

        return response.body()!!.searchResult.toDomainEntity()
    }

    @Throws(Exception::class)
    private fun search3(
        criteria: SearchCriteria
    ): SearchResult {
        requireNotNull(criteria.query) { "Query param is null" }
        val response = API.search3(
            criteria.query, criteria.artistCount, null, criteria.albumCount, null,
            criteria.songCount, null
        ).execute().throwOnFailure()

        return response.body()!!.searchResult.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getPlaylist(
        id: String,
        name: String
    ): MusicDirectory {
        val response = API.getPlaylist(id).execute().throwOnFailure()

        val playlist = response.body()!!.playlist.toMusicDirectoryDomainEntity()
        savePlaylist(name, playlist)

        return playlist
    }

    @Throws(IOException::class)
    private fun savePlaylist(
        name: String,
        playlist: MusicDirectory
    ) {
        val playlistFile = FileUtil.getPlaylistFile(
            activeServerProvider.getActiveServer().name, name
        )

        FileUtil.savePlaylist(playlistFile, playlist, name)
    }

    @Throws(Exception::class)
    override fun getPlaylists(
        refresh: Boolean
    ): List<Playlist> {
        val response = API.getPlaylists(null).execute().throwOnFailure()

        return response.body()!!.playlists.toDomainEntitiesList()
    }

    /**
     * Either ID or String is required.
     * ID is required when updating
     * String is required when creating
     */
    @Throws(Exception::class)
    override fun createPlaylist(
        id: String?,
        name: String?,
        entries: List<MusicDirectory.Entry>
    ) {
        if (id == null && name == null)
            throw IllegalArgumentException("Either id or name is required.")

        val pSongIds: MutableList<String> = ArrayList(entries.size)

        for ((id1) in entries) {
            pSongIds.add(id1)
        }

        API.createPlaylist(id, name, pSongIds.toList()).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun deletePlaylist(
        id: String
    ) {
        API.deletePlaylist(id).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun updatePlaylist(
        id: String,
        name: String?,
        comment: String?,
        pub: Boolean
    ) {
        API.updatePlaylist(id, name, comment, pub, null, null)
            .execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getPodcastsChannels(
        refresh: Boolean
    ): List<PodcastsChannel> {
        val response = API.getPodcasts(false, null).execute().throwOnFailure()

        return response.body()!!.podcastChannels.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(
        podcastChannelId: String?
    ): MusicDirectory {
        val response = API.getPodcasts(true, podcastChannelId).execute().throwOnFailure()

        val podcastEntries = response.body()!!.podcastChannels[0].episodeList
        val musicDirectory = MusicDirectory()

        for (podcastEntry in podcastEntries) {
            if (
                "skipped" != podcastEntry.status &&
                "error" != podcastEntry.status
            ) {
                val entry = podcastEntry.toDomainEntity()
                entry.track = null
                musicDirectory.addChild(entry)
            }
        }

        return musicDirectory
    }

    @Throws(Exception::class)
    override fun getLyrics(
        artist: String,
        title: String
    ): Lyrics {
        val response = API.getLyrics(artist, title).execute().throwOnFailure()

        return response.body()!!.lyrics.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun scrobble(
        id: String,
        submission: Boolean
    ) {
        API.scrobble(id, null, submission).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        val response = API.getAlbumList(
            fromName(type),
            size,
            offset,
            null,
            null,
            null,
            musicFolderId
        ).execute().throwOnFailure()

        val childList = response.body()!!.albumList.toDomainEntityList()
        val result = MusicDirectory()
        result.addAll(childList)

        return result
    }

    @Throws(Exception::class)
    override fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        val response = API.getAlbumList2(
            fromName(type),
            size,
            offset,
            null,
            null,
            null,
            musicFolderId
        ).execute().throwOnFailure()

        val result = MusicDirectory()
        result.addAll(response.body()!!.albumList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getRandomSongs(
        size: Int
    ): MusicDirectory {
        val response = API.getRandomSongs(
            size,
            null,
            null,
            null,
            null
        ).execute().throwOnFailure()

        val result = MusicDirectory()
        result.addAll(response.body()!!.songsList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getStarred(): SearchResult {
        val response = API.getStarred(null).execute().throwOnFailure()

        return response.body()!!.starred.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getStarred2(): SearchResult {
        val response = API.getStarred2(null).execute().throwOnFailure()

        return response.body()!!.starred2.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int,
        save: Boolean
    ): Pair<InputStream, Boolean> {
        val songOffset = if (offset < 0) 0 else offset
        lateinit var response: StreamResponse

        // Use semantically correct call
        if (save) {
            response = API.download(song.id, maxBitrate, offset = songOffset)
                .execute().toStreamResponse()
        } else {
            response = API.stream(song.id, maxBitrate, offset = songOffset)
                .execute().toStreamResponse()
        }

        response.throwOnFailure()

        if (response.stream == null) {
            throw IOException("Null stream response")
        }

        val partial = response.responseHttpCode == 206
        return Pair(response.stream!!, partial)
    }

    /**
     * We currently don't handle video playback in the app, but just create an Intent which video
     * players can respond to. For this intent we need the full URL of the stream, including the
     * authentication params. This is a bit tricky, because we want to avoid actually executing the
     * call because that could take a long time.
     */
    @Throws(Exception::class)
    override fun getVideoUrl(
        id: String
    ): String {
        // Create a new modified okhttp client to intercept the URL
        val builder = subsonicAPIClient.okHttpClient.newBuilder()

        builder.addInterceptor { chain ->
            // Returns a dummy response
            Response.Builder()
                .code(100)
                .body(ResponseBody.create(null, ""))
                .protocol(Protocol.HTTP_2)
                .message("Empty response")
                .request(chain.request())
                .build()
        }

        // Create a new Okhttp client
        val client = builder.build()

        // Get the request from Retrofit, but don't execute it!
        val request = API.stream(id, format = "raw").request()

        // Create a new call with the request, and execute ist on our custom client
        val response = client.newCall(request).execute()

        // The complete url :)
        val url = response.request().url()

        return url.toString()
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(
        ids: List<String>?
    ): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SET, null, null, ids, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun skipJukebox(
        index: Int,
        offsetSeconds: Int
    ): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SKIP, index, offsetSeconds, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun stopJukebox(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.STOP, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun startJukebox(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.START, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.STATUS, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun setJukeboxGain(
        gain: Float
    ): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SET_GAIN, null, null, null, gain)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getShares(
        refresh: Boolean
    ): List<Share> {
        val response = API.getShares().execute().throwOnFailure()

        return response.body()!!.shares.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun getGenres(
        refresh: Boolean
    ): List<Genre>? {
        val response = API.getGenres().execute().throwOnFailure()

        return response.body()!!.genresList.toDomainEntityList()
    }

    @Throws(Exception::class)
    override fun getSongsByGenre(
        genre: String,
        count: Int,
        offset: Int
    ): MusicDirectory {
        val response = API.getSongsByGenre(genre, count, offset, null).execute().throwOnFailure()

        val result = MusicDirectory()
        result.addAll(response.body()!!.songsList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getUser(
        username: String
    ): UserInfo {
        val response = API.getUser(username).execute().throwOnFailure()

        return response.body()!!.user.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getChatMessages(
        since: Long?
    ): List<ChatMessage> {
        val response = API.getChatMessages(since).execute().throwOnFailure()

        return response.body()!!.chatMessages.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun addChatMessage(
        message: String
    ) {
        API.addChatMessage(message).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getBookmarks(): List<Bookmark> {
        val response = API.getBookmarks().execute().throwOnFailure()

        return response.body()!!.bookmarkList.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun createBookmark(
        id: String,
        position: Int
    ) {
        API.createBookmark(id, position.toLong(), null).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun deleteBookmark(
        id: String
    ) {
        API.deleteBookmark(id).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getVideos(
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getVideos().execute().throwOnFailure()

        val musicDirectory = MusicDirectory()
        musicDirectory.addAll(response.body()!!.videosList.toDomainEntityList())

        return musicDirectory
    }

    @Throws(Exception::class)
    override fun createShare(
        ids: List<String>,
        description: String?,
        expires: Long?
    ): List<Share> {
        val response = API.createShare(ids, description, expires).execute().throwOnFailure()

        return response.body()!!.shares.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun deleteShare(
        id: String
    ) {
        API.deleteShare(id).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun updateShare(
        id: String,
        description: String?,
        expires: Long?
    ) {
        var expiresValue: Long? = expires
        if (expires != null && expires == 0L) {
            expiresValue = null
        }

        API.updateShare(id, description, expiresValue).execute().throwOnFailure()
    }

    init {
        // The client will notice if the minimum supported API version has changed
        // By registering a callback we ensure this info is saved in the database as well
        subsonicAPIClient.onProtocolChange = {
            Timber.i("Server minimum API version set to %s", it)
            activeServerProvider.setMinimumApiVersion(it.restApiVersion)
        }
    }
}
