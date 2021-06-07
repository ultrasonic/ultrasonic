/*
 * MusicService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.graphics.Bitmap
import java.io.InputStream
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

@Suppress("TooManyFunctions")
interface MusicService {
    @Throws(Exception::class)
    fun ping()

    @Throws(Exception::class)
    fun isLicenseValid(): Boolean

    @Throws(Exception::class)
    fun getGenres(refresh: Boolean): List<Genre>?

    @Throws(Exception::class)
    fun star(id: String?, albumId: String?, artistId: String?)

    @Throws(Exception::class)
    fun unstar(id: String?, albumId: String?, artistId: String?)

    @Throws(Exception::class)
    fun setRating(id: String, rating: Int)

    @Throws(Exception::class)
    fun getMusicFolders(refresh: Boolean): List<MusicFolder>

    @Throws(Exception::class)
    fun getIndexes(musicFolderId: String?, refresh: Boolean): Indexes

    @Throws(Exception::class)
    fun getArtists(refresh: Boolean): Indexes

    @Throws(Exception::class)
    fun getMusicDirectory(id: String, name: String?, refresh: Boolean): MusicDirectory

    @Throws(Exception::class)
    fun getArtist(id: String, name: String?, refresh: Boolean): MusicDirectory

    @Throws(Exception::class)
    fun getAlbum(id: String, name: String?, refresh: Boolean): MusicDirectory

    @Throws(Exception::class)
    fun search(criteria: SearchCriteria): SearchResult?

    @Throws(Exception::class)
    fun getPlaylist(id: String, name: String): MusicDirectory

    @Throws(Exception::class)
    fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel>

    @Throws(Exception::class)
    fun getPlaylists(refresh: Boolean): List<Playlist>

    @Throws(Exception::class)
    fun createPlaylist(id: String, name: String, entries: List<MusicDirectory.Entry>)

    @Throws(Exception::class)
    fun deletePlaylist(id: String)

    @Throws(Exception::class)
    fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean)

    @Throws(Exception::class)
    fun getLyrics(artist: String, title: String): Lyrics?

    @Throws(Exception::class)
    fun scrobble(id: String, submission: Boolean)

    @Throws(Exception::class)
    fun getAlbumList(type: String, size: Int, offset: Int, musicFolderId: String?): MusicDirectory

    @Throws(Exception::class)
    fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory

    @Throws(Exception::class)
    fun getRandomSongs(size: Int): MusicDirectory

    @Throws(Exception::class)
    fun getSongsByGenre(genre: String, count: Int, offset: Int): MusicDirectory

    @Throws(Exception::class)
    fun getStarred(): SearchResult

    @Throws(Exception::class)
    fun getStarred2(): SearchResult

    @Throws(Exception::class)
    fun getCoverArt(
        entry: MusicDirectory.Entry,
        size: Int,
        saveToFile: Boolean
    ): Bitmap?

    @Throws(Exception::class)
    fun getAvatar(
        username: String?,
        size: Int,
        saveToFile: Boolean
    ): Bitmap?

    /**
     * Return response [InputStream] and a [Boolean] that indicates if this response is
     * partial.
     */
    @Throws(Exception::class)
    fun getDownloadInputStream(
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int
    ): Pair<InputStream, Boolean>

    // TODO: Refactor and remove this call (see RestMusicService implementation)
    @Throws(Exception::class)
    fun getVideoUrl(id: String, useFlash: Boolean): String?

    @Throws(Exception::class)
    fun updateJukeboxPlaylist(ids: List<String>?): JukeboxStatus

    @Throws(Exception::class)
    fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus

    @Throws(Exception::class)
    fun stopJukebox(): JukeboxStatus

    @Throws(Exception::class)
    fun startJukebox(): JukeboxStatus

    @Throws(Exception::class)
    fun getJukeboxStatus(): JukeboxStatus

    @Throws(Exception::class)
    fun setJukeboxGain(gain: Float): JukeboxStatus

    @Throws(Exception::class)
    fun getShares(refresh: Boolean): List<Share>

    @Throws(Exception::class)
    fun getChatMessages(since: Long?): List<ChatMessage?>?

    @Throws(Exception::class)
    fun addChatMessage(message: String)

    @Throws(Exception::class)
    fun getBookmarks(): List<Bookmark?>?

    @Throws(Exception::class)
    fun deleteBookmark(id: String)

    @Throws(Exception::class)
    fun createBookmark(id: String, position: Int)

    @Throws(Exception::class)
    fun getVideos(refresh: Boolean): MusicDirectory?

    @Throws(Exception::class)
    fun getUser(username: String): UserInfo

    @Throws(Exception::class)
    fun createShare(ids: List<String>, description: String?, expires: Long?): List<Share>

    @Throws(Exception::class)
    fun deleteShare(id: String)

    @Throws(Exception::class)
    fun updateShare(id: String, description: String?, expires: Long?)

    @Throws(Exception::class)
    fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory?
}
