/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.service

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.Companion.fromName
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.cache.PermanentFileStorage
import org.moire.ultrasonic.cache.serializers.getIndexesSerializer
import org.moire.ultrasonic.cache.serializers.getMusicFolderListSerializer
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isServerScalingEnabled
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.ChatMessage
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Indexes
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
import org.moire.ultrasonic.domain.toDomainEntitiesList
import org.moire.ultrasonic.domain.toDomainEntity
import org.moire.ultrasonic.domain.toDomainEntityList
import org.moire.ultrasonic.domain.toMusicDirectoryDomainEntity
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * This Music Service implementation connects to a server using the Subsonic REST API
 * @author Sindre Mehus
 */
open class RESTMusicService(
    private val subsonicAPIClient: SubsonicAPIClient,
    private val fileStorage: PermanentFileStorage,
    private val activeServerProvider: ActiveServerProvider,
    private val responseChecker: ApiCallResponseChecker
) : MusicService {

    @Throws(Exception::class)
    override fun ping() {
        responseChecker.callWithResponseCheck { api -> api.ping().execute() }
    }

    @Throws(Exception::class)
    override fun isLicenseValid(): Boolean {
        val response = responseChecker.callWithResponseCheck { api -> api.getLicense().execute() }

        return response.body()!!.license.valid
    }

    @Throws(Exception::class)
    override fun getMusicFolders(
        refresh: Boolean
    ): List<MusicFolder> {
        val cachedMusicFolders = fileStorage.load(
            MUSIC_FOLDER_STORAGE_NAME, getMusicFolderListSerializer()
        )

        if (cachedMusicFolders != null && !refresh) return cachedMusicFolders

        val response = responseChecker.callWithResponseCheck { api ->
            api.getMusicFolders().execute()
        }

        val musicFolders = response.body()!!.musicFolders.toDomainEntityList()
        fileStorage.store(MUSIC_FOLDER_STORAGE_NAME, musicFolders, getMusicFolderListSerializer())

        return musicFolders
    }

    @Throws(Exception::class)
    override fun getIndexes(
        musicFolderId: String?,
        refresh: Boolean,
        context: Context
    ): Indexes {
        val indexName = INDEXES_STORAGE_NAME + (musicFolderId ?: "")

        val cachedIndexes = fileStorage.load(indexName, getIndexesSerializer())
        if (cachedIndexes != null && !refresh) return cachedIndexes

        val response = responseChecker.callWithResponseCheck { api ->
            api.getIndexes(musicFolderId, null).execute()
        }

        val indexes = response.body()!!.indexes.toDomainEntity()
        fileStorage.store(indexName, indexes, getIndexesSerializer())
        return indexes
    }

    @Throws(Exception::class)
    override fun getArtists(
        refresh: Boolean
    ): Indexes {
        val cachedArtists = fileStorage.load(ARTISTS_STORAGE_NAME, getIndexesSerializer())
        if (cachedArtists != null && !refresh) return cachedArtists

        val response = responseChecker.callWithResponseCheck { api ->
            api.getArtists(null).execute()
        }

        val indexes = response.body()!!.indexes.toDomainEntity()
        fileStorage.store(ARTISTS_STORAGE_NAME, indexes, getIndexesSerializer())
        return indexes
    }

    @Throws(Exception::class)
    override fun star(
        id: String?,
        albumId: String?,
        artistId: String?
    ) {
        responseChecker.callWithResponseCheck { api -> api.star(id, albumId, artistId).execute() }
    }

    @Throws(Exception::class)
    override fun unstar(
        id: String?,
        albumId: String?,
        artistId: String?
    ) {
        responseChecker.callWithResponseCheck { api -> api.unstar(id, albumId, artistId).execute() }
    }

    @Throws(Exception::class)
    override fun setRating(
        id: String,
        rating: Int
    ) {
        responseChecker.callWithResponseCheck { api -> api.setRating(id, rating).execute() }
    }

    @Throws(Exception::class)
    override fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getMusicDirectory(id).execute()
        }

        return response.body()!!.musicDirectory.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getArtist(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api -> api.getArtist(id).execute() }

        return response.body()!!.artist.toMusicDirectoryDomainEntity()
    }

    @Throws(Exception::class)
    override fun getAlbum(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api -> api.getAlbum(id).execute() }

        return response.body()!!.album.toMusicDirectoryDomainEntity()
    }

    @Throws(Exception::class)
    override fun search(
        criteria: SearchCriteria,
        context: Context
    ): SearchResult {
        return try {
            if (
                !isOffline(context) &&
                Util.getShouldUseId3Tags()
            ) search3(criteria)
            else search2(criteria)
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
        val response = responseChecker.callWithResponseCheck { api ->
            api.search(null, null, null, criteria.query, criteria.songCount, null, null)
                .execute()
        }

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
        val response = responseChecker.callWithResponseCheck { api ->
            api.search2(
                criteria.query, criteria.artistCount, null, criteria.albumCount, null,
                criteria.songCount, null
            ).execute()
        }

        return response.body()!!.searchResult.toDomainEntity()
    }

    @Throws(Exception::class)
    private fun search3(
        criteria: SearchCriteria
    ): SearchResult {
        requireNotNull(criteria.query) { "Query param is null" }
        val response = responseChecker.callWithResponseCheck { api ->
            api.search3(
                criteria.query, criteria.artistCount, null, criteria.albumCount, null,
                criteria.songCount, null
            ).execute()
        }

        return response.body()!!.searchResult.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getPlaylist(
        id: String,
        name: String?,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getPlaylist(id).execute()
        }

        val playlist = response.body()!!.playlist.toMusicDirectoryDomainEntity()
        savePlaylist(name, context, playlist)

        return playlist
    }

    @Throws(IOException::class)
    private fun savePlaylist(
        name: String?,
        context: Context,
        playlist: MusicDirectory
    ) {
        val playlistFile = FileUtil.getPlaylistFile(
            context, activeServerProvider.getActiveServer().name, name
        )

        val fw = FileWriter(playlistFile)
        val bw = BufferedWriter(fw)

        try {
            fw.write("#EXTM3U\n")
            for (e in playlist.getChildren()) {
                var filePath = FileUtil.getSongFile(context, e).absolutePath

                if (!File(filePath).exists()) {
                    val ext = FileUtil.getExtension(filePath)
                    val base = FileUtil.getBaseName(filePath)
                    filePath = "$base.complete.$ext"
                }
                fw.write(filePath + "\n")
            }
        } catch (e: IOException) {
            Timber.w("Failed to save playlist: %s", name)
            throw e
        } finally {
            bw.close()
            fw.close()
        }
    }

    @Throws(Exception::class)
    override fun getPlaylists(
        refresh: Boolean,
        context: Context
    ): List<Playlist> {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getPlaylists(null).execute()
        }

        return response.body()!!.playlists.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun createPlaylist(
        id: String?,
        name: String?,
        entries: List<MusicDirectory.Entry>,
        context: Context
    ) {
        val pSongIds: MutableList<String> = ArrayList(entries.size)

        for ((id1) in entries) {
            if (id1 != null) {
                pSongIds.add(id1)
            }
        }
        responseChecker.callWithResponseCheck { api ->
            api.createPlaylist(id, name, pSongIds.toList()).execute()
        }
    }

    @Throws(Exception::class)
    override fun deletePlaylist(
        id: String,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api -> api.deletePlaylist(id).execute() }
    }

    @Throws(Exception::class)
    override fun updatePlaylist(
        id: String,
        name: String?,
        comment: String?,
        pub: Boolean,
        context: Context?
    ) {
        responseChecker.callWithResponseCheck { api ->
            api.updatePlaylist(id, name, comment, pub, null, null)
                .execute()
        }
    }

    @Throws(Exception::class)
    override fun getPodcastsChannels(
        refresh: Boolean,
        context: Context
    ): List<PodcastsChannel> {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getPodcasts(false, null).execute()
        }

        return response.body()!!.podcastChannels.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(
        podcastChannelId: String?,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getPodcasts(true, podcastChannelId).execute()
        }

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
        artist: String?,
        title: String?,
        context: Context
    ): Lyrics {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getLyrics(artist, title).execute()
        }

        return response.body()!!.lyrics.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun scrobble(
        id: String,
        submission: Boolean,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api ->
            api.scrobble(id, null, submission).execute()
        }
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getAlbumList(fromName(type), size, offset, null, null, null, musicFolderId)
                .execute()
        }

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
        val response = responseChecker.callWithResponseCheck { api ->
            api.getAlbumList2(
                fromName(type),
                size,
                offset,
                null,
                null,
                null,
                musicFolderId
            ).execute()
        }

        val result = MusicDirectory()
        result.addAll(response.body()!!.albumList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getRandomSongs(
        size: Int,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getRandomSongs(
                size,
                null,
                null,
                null,
                null
            ).execute()
        }

        val result = MusicDirectory()
        result.addAll(response.body()!!.songsList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getStarred(): SearchResult {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getStarred(null).execute()
        }

        return response.body()!!.starred.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getStarred2(): SearchResult {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getStarred2(null).execute()
        }

        return response.body()!!.starred2.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getCoverArt(
        context: Context,
        entry: MusicDirectory.Entry?,
        size: Int,
        saveToFile: Boolean,
        highQuality: Boolean
    ): Bitmap? {
        // Synchronize on the entry so that we don't download concurrently for
        // the same song.
        if (entry == null) {
            return null
        }

        synchronized(entry) {
            // Use cached file, if existing.
            var bitmap = FileUtil.getAlbumArtBitmap(context, entry, size, highQuality)
            val serverScaling = isServerScalingEnabled(context)

            if (bitmap == null) {
                Timber.d("Loading cover art for: %s", entry)

                val id = entry.coverArt

                if (TextUtils.isEmpty(id)) {
                    return null // Can't load
                }

                val response = subsonicAPIClient.getCoverArt(id!!, size.toLong())
                checkStreamResponseError(response)

                if (response.stream == null) {
                    return null // Failed to load
                }

                var inputStream: InputStream? = null
                try {
                    inputStream = response.stream
                    val bytes = Util.toByteArray(inputStream)

                    // If we aren't allowing server-side scaling, always save the file to disk
                    // because it will be unmodified
                    if (!serverScaling || saveToFile) {
                        var outputStream: OutputStream? = null
                        try {
                            outputStream = FileOutputStream(
                                FileUtil.getAlbumArtFile(context, entry)
                            )
                            outputStream.write(bytes)
                        } finally {
                            Util.close(outputStream)
                        }
                    }

                    bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality)
                } finally {
                    Util.close(inputStream)
                }
            }

            // Return scaled bitmap
            return Util.scaleBitmap(bitmap, size)
        }
    }

    @Throws(SubsonicRESTException::class, IOException::class)
    private fun checkStreamResponseError(response: StreamResponse) {
        if (response.hasError() || response.stream == null) {
            if (response.apiError != null) {
                throw SubsonicRESTException(response.apiError!!)
            } else {
                throw IOException(
                    "Failed to make endpoint request, code: " + response.responseHttpCode
                )
            }
        }
    }

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int
    ): Pair<InputStream, Boolean> {
        val songOffset = if (offset < 0) 0 else offset

        val response = subsonicAPIClient.stream(song.id!!, maxBitrate, songOffset)
        checkStreamResponseError(response)

        if (response.stream == null) {
            throw IOException("Null stream response")
        }

        val partial = response.responseHttpCode == 206
        return Pair(response.stream!!, partial)
    }

    @Throws(Exception::class)
    override fun getVideoUrl(
        context: Context,
        id: String,
        useFlash: Boolean
    ): String {
        // TODO This method should not exists as video should be loaded using stream method
        // Previous method implementation uses assumption that video will be available
        // by videoPlayer.view?id=<id>&maxBitRate=500&autoplay=true, but this url is not
        // official Subsonic API call.
        val expectedResult = arrayOfNulls<String>(1)
        expectedResult[0] = null

        val latch = CountDownLatch(1)

        Thread(
            {
                expectedResult[0] = subsonicAPIClient.getStreamUrl(id) + "&format=raw"
                latch.countDown()
            },
            "Get-Video-Url"
        ).start()

        latch.await(5, TimeUnit.SECONDS)

        return expectedResult[0]!!
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(
        ids: List<String>?,
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.SET, null, null, ids, null)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun skipJukebox(
        index: Int,
        offsetSeconds: Int,
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.SKIP, index, offsetSeconds, null, null)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun stopJukebox(
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.STOP, null, null, null, null)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun startJukebox(
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.START, null, null, null, null)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.STATUS, null, null, null, null)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun setJukeboxGain(
        gain: Float,
        context: Context
    ): JukeboxStatus {
        val response = responseChecker.callWithResponseCheck { api ->
            api.jukeboxControl(JukeboxAction.SET_GAIN, null, null, null, gain)
                .execute()
        }

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getShares(
        refresh: Boolean,
        context: Context
    ): List<Share> {
        val response = responseChecker.callWithResponseCheck { api -> api.getShares().execute() }

        return response.body()!!.shares.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun getGenres(
        refresh: Boolean
    ): List<Genre> {
        val response = responseChecker.callWithResponseCheck { api -> api.getGenres().execute() }

        return response.body()!!.genresList.toDomainEntityList()
    }

    @Throws(Exception::class)
    override fun getSongsByGenre(
        genre: String,
        count: Int,
        offset: Int,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getSongsByGenre(genre, count, offset, null).execute()
        }

        val result = MusicDirectory()
        result.addAll(response.body()!!.songsList.toDomainEntityList())

        return result
    }

    @Throws(Exception::class)
    override fun getUser(
        username: String,
        context: Context
    ): UserInfo {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getUser(username).execute()
        }

        return response.body()!!.user.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getChatMessages(
        since: Long?,
        context: Context
    ): List<ChatMessage> {
        val response = responseChecker.callWithResponseCheck { api ->
            api.getChatMessages(since).execute()
        }

        return response.body()!!.chatMessages.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun addChatMessage(
        message: String,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api -> api.addChatMessage(message).execute() }
    }

    @Throws(Exception::class)
    override fun getBookmarks(
        context: Context
    ): List<Bookmark> {
        val response = responseChecker.callWithResponseCheck { api -> api.getBookmarks().execute() }

        return response.body()!!.bookmarkList.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun createBookmark(
        id: String,
        position: Int,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api ->
            api.createBookmark(id, position.toLong(), null).execute()
        }
    }

    @Throws(Exception::class)
    override fun deleteBookmark(
        id: String,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api -> api.deleteBookmark(id).execute() }
    }

    @Throws(Exception::class)
    override fun getVideos(
        refresh: Boolean,
        context: Context
    ): MusicDirectory {
        val response = responseChecker.callWithResponseCheck { api -> api.getVideos().execute() }

        val musicDirectory = MusicDirectory()
        musicDirectory.addAll(response.body()!!.videosList.toDomainEntityList())

        return musicDirectory
    }

    @Throws(Exception::class)
    override fun createShare(
        ids: List<String>,
        description: String?,
        expires: Long?,
        context: Context
    ): List<Share> {
        val response = responseChecker.callWithResponseCheck { api ->
            api.createShare(ids, description, expires).execute()
        }

        return response.body()!!.shares.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun deleteShare(
        id: String,
        context: Context
    ) {
        responseChecker.callWithResponseCheck { api -> api.deleteShare(id).execute() }
    }

    @Throws(Exception::class)
    override fun updateShare(
        id: String,
        description: String?,
        expires: Long?,
        context: Context
    ) {
        var expiresValue: Long? = expires
        if (expires != null && expires == 0L) {
            expiresValue = null
        }

        responseChecker.callWithResponseCheck { api ->
            api.updateShare(id, description, expiresValue).execute()
        }
    }

    @Throws(Exception::class)
    override fun getAvatar(
        context: Context,
        username: String?,
        size: Int,
        saveToFile: Boolean,
        highQuality: Boolean
    ): Bitmap? {
        // Synchronize on the username so that we don't download concurrently for
        // the same user.
        if (username == null) {
            return null
        }

        synchronized(username) {
            // Use cached file, if existing.
            var bitmap = FileUtil.getAvatarBitmap(context, username, size, highQuality)

            if (bitmap == null) {
                var inputStream: InputStream? = null
                try {
                    val response = subsonicAPIClient.getAvatar(username)

                    if (response.hasError()) return null

                    inputStream = response.stream
                    val bytes = Util.toByteArray(inputStream)

                    // If we aren't allowing server-side scaling, always save the file to disk
                    // because it will be unmodified
                    if (saveToFile) {
                        var outputStream: OutputStream? = null

                        try {
                            outputStream = FileOutputStream(
                                FileUtil.getAvatarFile(context, username)
                            )
                            outputStream.write(bytes)
                        } finally {
                            Util.close(outputStream)
                        }
                    }

                    bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality)
                } finally {
                    Util.close(inputStream)
                }
            }

            // Return scaled bitmap
            return Util.scaleBitmap(bitmap, size)
        }
    }

    companion object {
        private const val MUSIC_FOLDER_STORAGE_NAME = "music_folder"
        private const val INDEXES_STORAGE_NAME = "indexes"
        private const val INDEXES_FOLDER_STORAGE_NAME = "indexes_folder"
        private const val ARTISTS_STORAGE_NAME = "artists"
    }
}
