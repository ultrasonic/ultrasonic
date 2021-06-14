/*
 * CachedMusicService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import java.io.InputStream
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
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
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.LRUCache
import org.moire.ultrasonic.util.TimeLimitedCache
import org.moire.ultrasonic.util.Util

@Suppress("TooManyFunctions")
class CachedMusicService(private val musicService: MusicService) : MusicService, KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    private val cachedMusicDirectories: LRUCache<String?, TimeLimitedCache<MusicDirectory?>>
    private val cachedArtist: LRUCache<String?, TimeLimitedCache<MusicDirectory?>>
    private val cachedAlbum: LRUCache<String?, TimeLimitedCache<MusicDirectory?>>
    private val cachedUserInfo: LRUCache<String?, TimeLimitedCache<UserInfo?>>
    private val cachedLicenseValid = TimeLimitedCache<Boolean>(expiresAfter = 10, TimeUnit.MINUTES)
    private val cachedIndexes = TimeLimitedCache<Indexes?>()
    private val cachedArtists = TimeLimitedCache<Indexes?>()
    private val cachedPlaylists = TimeLimitedCache<List<Playlist>?>()
    private val cachedPodcastsChannels = TimeLimitedCache<List<PodcastsChannel>>()
    private val cachedMusicFolders =
        TimeLimitedCache<List<MusicFolder>?>(10, TimeUnit.HOURS)
    private val cachedGenres = TimeLimitedCache<List<Genre>?>(10, TimeUnit.HOURS)
    private var restUrl: String? = null
    private var cachedMusicFolderId: String? = null

    @Throws(Exception::class)
    override fun ping() {
        checkSettingsChanged()
        musicService.ping()
    }

    @Throws(Exception::class)
    override fun isLicenseValid(): Boolean {
        checkSettingsChanged()
        var isValid = cachedLicenseValid.get()
        if (isValid == null) {
            isValid = musicService.isLicenseValid()
            cachedLicenseValid.set(isValid)
        }
        return isValid
    }

    @Throws(Exception::class)
    override fun getMusicFolders(refresh: Boolean): List<MusicFolder> {
        checkSettingsChanged()
        if (refresh) {
            cachedMusicFolders.clear()
        }

        val cache = cachedMusicFolders.get()
        if (cache != null) return cache

        val result = musicService.getMusicFolders(refresh)
        cachedMusicFolders.set(result)

        return result
    }

    @Throws(Exception::class)
    override fun getIndexes(musicFolderId: String?, refresh: Boolean): Indexes {
        checkSettingsChanged()
        if (refresh) {
            cachedIndexes.clear()
            cachedMusicFolders.clear()
            cachedMusicDirectories.clear()
        }
        var result = cachedIndexes.get()
        if (result == null) {
            result = musicService.getIndexes(musicFolderId, refresh)
            cachedIndexes.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override fun getArtists(refresh: Boolean): Indexes {
        checkSettingsChanged()
        if (refresh) {
            cachedArtists.clear()
        }
        var result = cachedArtists.get()
        if (result == null) {
            result = musicService.getArtists(refresh)
            cachedArtists.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override fun getMusicDirectory(id: String, name: String?, refresh: Boolean): MusicDirectory {
        checkSettingsChanged()
        var cache = if (refresh) null else cachedMusicDirectories[id]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getMusicDirectory(id, name, refresh)
            cache = TimeLimitedCache(
                Util.getDirectoryCacheTime().toLong(), TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedMusicDirectories.put(id, cache)
        }
        return dir
    }

    @Throws(Exception::class)
    override fun getArtist(id: String, name: String?, refresh: Boolean): MusicDirectory {
        checkSettingsChanged()
        var cache = if (refresh) null else cachedArtist[id]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getArtist(id, name, refresh)
            cache = TimeLimitedCache(
                Util.getDirectoryCacheTime().toLong(), TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedArtist.put(id, cache)
        }
        return dir
    }

    @Throws(Exception::class)
    override fun getAlbum(id: String, name: String?, refresh: Boolean): MusicDirectory {
        checkSettingsChanged()
        var cache = if (refresh) null else cachedAlbum[id]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getAlbum(id, name, refresh)
            cache = TimeLimitedCache(
                Util.getDirectoryCacheTime().toLong(), TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedAlbum.put(id, cache)
        }
        return dir
    }

    @Throws(Exception::class)
    override fun search(criteria: SearchCriteria): SearchResult? {
        return musicService.search(criteria)
    }

    @Throws(Exception::class)
    override fun getPlaylist(id: String, name: String): MusicDirectory {
        return musicService.getPlaylist(id, name)
    }

    @Throws(Exception::class)
    override fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel> {
        checkSettingsChanged()
        var result = if (refresh) null else cachedPodcastsChannels.get()
        if (result == null) {
            result = musicService.getPodcastsChannels(refresh)
            cachedPodcastsChannels.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory? {
        return musicService.getPodcastEpisodes(podcastChannelId)
    }

    @Throws(Exception::class)
    override fun getPlaylists(refresh: Boolean): List<Playlist> {
        checkSettingsChanged()
        var result = if (refresh) null else cachedPlaylists.get()
        if (result == null) {
            result = musicService.getPlaylists(refresh)
            cachedPlaylists.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override fun createPlaylist(id: String, name: String, entries: List<MusicDirectory.Entry>) {
        cachedPlaylists.clear()
        musicService.createPlaylist(id, name, entries)
    }

    @Throws(Exception::class)
    override fun deletePlaylist(id: String) {
        musicService.deletePlaylist(id)
    }

    @Throws(Exception::class)
    override fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean) {
        musicService.updatePlaylist(id, name, comment, pub)
    }

    @Throws(Exception::class)
    override fun getLyrics(artist: String, title: String): Lyrics? {
        return musicService.getLyrics(artist, title)
    }

    @Throws(Exception::class)
    override fun scrobble(id: String, submission: Boolean) {
        musicService.scrobble(id, submission)
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        return musicService.getAlbumList(type, size, offset, musicFolderId)
    }

    @Throws(Exception::class)
    override fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        return musicService.getAlbumList2(type, size, offset, musicFolderId)
    }

    @Throws(Exception::class)
    override fun getRandomSongs(size: Int): MusicDirectory {
        return musicService.getRandomSongs(size)
    }

    @Throws(Exception::class)
    override fun getStarred(): SearchResult = musicService.getStarred()

    @Throws(Exception::class)
    override fun getStarred2(): SearchResult = musicService.getStarred2()

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int
    ): Pair<InputStream, Boolean> {
        return musicService.getDownloadInputStream(song, offset, maxBitrate)
    }

    @Throws(Exception::class)
    override fun getVideoUrl(id: String): String? {
        return musicService.getVideoUrl(id)
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(ids: List<String>?): JukeboxStatus {
        return musicService.updateJukeboxPlaylist(ids)
    }

    @Throws(Exception::class)
    override fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus {
        return musicService.skipJukebox(index, offsetSeconds)
    }

    @Throws(Exception::class)
    override fun stopJukebox(): JukeboxStatus {
        return musicService.stopJukebox()
    }

    @Throws(Exception::class)
    override fun startJukebox(): JukeboxStatus {
        return musicService.startJukebox()
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(): JukeboxStatus = musicService.getJukeboxStatus()

    @Throws(Exception::class)
    override fun setJukeboxGain(gain: Float): JukeboxStatus {
        return musicService.setJukeboxGain(gain)
    }

    private fun checkSettingsChanged() {
        val newUrl = activeServerProvider.getRestUrl(null)
        val newFolderId = activeServerProvider.getActiveServer().musicFolderId
        if (!Util.equals(newUrl, restUrl) || !Util.equals(cachedMusicFolderId, newFolderId)) {
            cachedMusicFolders.clear()
            cachedMusicDirectories.clear()
            cachedLicenseValid.clear()
            cachedIndexes.clear()
            cachedPlaylists.clear()
            cachedGenres.clear()
            cachedAlbum.clear()
            cachedArtist.clear()
            cachedUserInfo.clear()
            restUrl = newUrl
            cachedMusicFolderId = newFolderId
        }
    }

    @Throws(Exception::class)
    override fun star(id: String?, albumId: String?, artistId: String?) {
        musicService.star(id, albumId, artistId)
    }

    @Throws(Exception::class)
    override fun unstar(id: String?, albumId: String?, artistId: String?) {
        musicService.unstar(id, albumId, artistId)
    }

    @Throws(Exception::class)
    override fun setRating(id: String, rating: Int) {
        musicService.setRating(id, rating)
    }

    @Throws(Exception::class)
    override fun getGenres(refresh: Boolean): List<Genre>? {
        checkSettingsChanged()
        if (refresh) {
            cachedGenres.clear()
        }
        var result = cachedGenres.get()
        if (result == null) {
            result = musicService.getGenres(refresh)
            cachedGenres.set(result)
        }

        val sorted = result?.toMutableList()
        sorted?.sortWith { genre, genre2 ->
            genre.name.compareTo(
                genre2.name,
                ignoreCase = true
            )
        }
        return sorted
    }

    @Throws(Exception::class)
    override fun getSongsByGenre(genre: String, count: Int, offset: Int): MusicDirectory {
        return musicService.getSongsByGenre(genre, count, offset)
    }

    @Throws(Exception::class)
    override fun getShares(refresh: Boolean): List<Share> {
        return musicService.getShares(refresh)
    }

    @Throws(Exception::class)
    override fun getChatMessages(since: Long?): List<ChatMessage?>? {
        return musicService.getChatMessages(since)
    }

    @Throws(Exception::class)
    override fun addChatMessage(message: String) {
        musicService.addChatMessage(message)
    }

    @Throws(Exception::class)
    override fun getBookmarks(): List<Bookmark?>? = musicService.getBookmarks()

    @Throws(Exception::class)
    override fun deleteBookmark(id: String) {
        musicService.deleteBookmark(id)
    }

    @Throws(Exception::class)
    override fun createBookmark(id: String, position: Int) {
        musicService.createBookmark(id, position)
    }

    @Throws(Exception::class)
    override fun getVideos(refresh: Boolean): MusicDirectory? {
        checkSettingsChanged()
        var cache =
            if (refresh) null else cachedMusicDirectories[Constants.INTENT_EXTRA_NAME_VIDEOS]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getVideos(refresh)
            cache = TimeLimitedCache(
                Util.getDirectoryCacheTime().toLong(), TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedMusicDirectories.put(Constants.INTENT_EXTRA_NAME_VIDEOS, cache)
        }
        return dir
    }

    @Throws(Exception::class)
    override fun getUser(username: String): UserInfo {
        checkSettingsChanged()
        var cache = cachedUserInfo[username]
        var userInfo = cache?.get()
        if (userInfo == null) {
            userInfo = musicService.getUser(username)
            cache = TimeLimitedCache(
                Util.getDirectoryCacheTime().toLong(), TimeUnit.SECONDS
            )
            cache.set(userInfo)
            cachedUserInfo.put(username, cache)
        }
        return userInfo
    }

    @Throws(Exception::class)
    override fun createShare(
        ids: List<String>,
        description: String?,
        expires: Long?
    ): List<Share> {
        return musicService.createShare(ids, description, expires)
    }

    @Throws(Exception::class)
    override fun deleteShare(id: String) {
        musicService.deleteShare(id)
    }

    @Throws(Exception::class)
    override fun updateShare(id: String, description: String?, expires: Long?) {
        musicService.updateShare(id, description, expires)
    }

    companion object {
        private const val MUSIC_DIR_CACHE_SIZE = 100
    }

    init {
        cachedMusicDirectories = LRUCache(MUSIC_DIR_CACHE_SIZE)
        cachedArtist = LRUCache(MUSIC_DIR_CACHE_SIZE)
        cachedAlbum = LRUCache(MUSIC_DIR_CACHE_SIZE)
        cachedUserInfo = LRUCache(MUSIC_DIR_CACHE_SIZE)
    }
}
