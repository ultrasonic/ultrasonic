/*
 * OfflineMusicService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.media.MediaMetadataRetriever
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.io.Reader
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedList
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
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
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

// TODO: There are quite a number of deeply nested and complicated functions in this class..
// Simplify them :)
@Suppress("TooManyFunctions")
class OfflineMusicService : MusicService, KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    override fun getIndexes(musicFolderId: String?, refresh: Boolean): Indexes {
        val artists: MutableList<Artist> = ArrayList()
        val root = FileUtil.getMusicDirectory()
        for (file in FileUtil.listFiles(root)) {
            if (file.isDirectory) {
                val artist = Artist()
                artist.id = file.path
                artist.index = file.name.substring(0, 1)
                artist.name = file.name
                artists.add(artist)
            }
        }
        val ignoredArticlesString = "The El La Los Las Le Les"
        val ignoredArticles = COMPILE.split(ignoredArticlesString)
        artists.sortWith { lhsArtist, rhsArtist ->
            var lhs = lhsArtist.name!!.lowercase(Locale.ROOT)
            var rhs = rhsArtist.name!!.lowercase(Locale.ROOT)
            val lhs1 = lhs[0]
            val rhs1 = rhs[0]
            if (Character.isDigit(lhs1) && !Character.isDigit(rhs1)) {
                return@sortWith 1
            }
            if (Character.isDigit(rhs1) && !Character.isDigit(lhs1)) {
                return@sortWith -1
            }
            for (article in ignoredArticles) {
                var index = lhs.indexOf(
                    String.format(Locale.ROOT, "%s ", article.lowercase(Locale.ROOT))
                )
                if (index == 0) {
                    lhs = lhs.substring(article.length + 1)
                }
                index = rhs.indexOf(
                    String.format(Locale.ROOT, "%s ", article.lowercase(Locale.ROOT))
                )
                if (index == 0) {
                    rhs = rhs.substring(article.length + 1)
                }
            }
            lhs.compareTo(rhs)
        }

        return Indexes(0L, ignoredArticlesString, artists = artists)
    }

    override fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val dir = File(id)
        val result = MusicDirectory()
        result.name = dir.name

        val seen: MutableCollection<String?> = HashSet()

        for (file in FileUtil.listMediaFiles(dir)) {
            val filename = getName(file)
            if (filename != null && !seen.contains(filename)) {
                seen.add(filename)
                result.addChild(createEntry(file, filename))
            }
        }

        return result
    }

    override fun search(criteria: SearchCriteria): SearchResult {
        val artists: MutableList<Artist> = ArrayList()
        val albums: MutableList<MusicDirectory.Entry> = ArrayList()
        val songs: MutableList<MusicDirectory.Entry> = ArrayList()
        val root = FileUtil.getMusicDirectory()
        var closeness: Int
        for (artistFile in FileUtil.listFiles(root)) {
            val artistName = artistFile.name
            if (artistFile.isDirectory) {
                if (matchCriteria(criteria, artistName).also { closeness = it } > 0) {
                    val artist = Artist()
                    artist.id = artistFile.path
                    artist.index = artistFile.name.substring(0, 1)
                    artist.name = artistName
                    artist.closeness = closeness
                    artists.add(artist)
                }
                recursiveAlbumSearch(artistName, artistFile, criteria, albums, songs)
            }
        }

        artists.sort()
        albums.sort()
        songs.sort()

        return SearchResult(artists, albums, songs)
    }

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
    override fun getPlaylists(refresh: Boolean): List<Playlist> {
        val playlists: MutableList<Playlist> = ArrayList()
        val root = FileUtil.getPlaylistDirectory()
        var lastServer: String? = null
        var removeServer = true
        for (folder in FileUtil.listFiles(root)) {
            if (folder.isDirectory) {
                val server = folder.name
                val fileList = FileUtil.listFiles(folder)
                for (file in fileList) {
                    if (FileUtil.isPlaylistFile(file)) {
                        val id = file.name
                        val filename = server + ": " + FileUtil.getBaseName(id)
                        val playlist = Playlist(server, filename)
                        playlists.add(playlist)
                    }
                }
                if (server != lastServer && !fileList.isEmpty()) {
                    if (lastServer != null) {
                        removeServer = false
                    }
                    lastServer = server
                }
            } else {
                // Delete legacy playlist files
                try {
                    if (!folder.delete()) {
                        Timber.w("Failed to delete old playlist file: %s", folder.name)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to delete old playlist file: %s", folder.name)
                }
            }
        }
        if (removeServer) {
            for (playlist in playlists) {
                playlist.name = playlist.name.substring(playlist.id.length + 2)
            }
        }
        return playlists
    }

    @Throws(Exception::class)
    override fun getPlaylist(id: String, name: String): MusicDirectory {
        var playlistName = name
        var reader: Reader? = null
        var buffer: BufferedReader? = null

        return try {
            val firstIndex = playlistName.indexOf(id)
            if (firstIndex != -1) {
                playlistName = playlistName.substring(id.length + 2)
            }
            val playlistFile = FileUtil.getPlaylistFile(id, playlistName)
            reader = FileReader(playlistFile)
            buffer = BufferedReader(reader)
            val playlist = MusicDirectory()
            var line = buffer.readLine()
            if ("#EXTM3U" != line) return playlist
            while (buffer.readLine().also { line = it } != null) {
                val entryFile = File(line)
                val entryName = getName(entryFile)
                if (entryFile.exists() && entryName != null) {
                    playlist.addChild(createEntry(entryFile, entryName))
                }
            }
            playlist
        } finally {
            Util.close(buffer)
            Util.close(reader)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    @Throws(Exception::class)
    override fun createPlaylist(id: String, name: String, entries: List<MusicDirectory.Entry>) {
        val playlistFile =
            FileUtil.getPlaylistFile(activeServerProvider.getActiveServer().name, name)
        val fw = FileWriter(playlistFile)
        val bw = BufferedWriter(fw)
        try {
            fw.write("#EXTM3U\n")
            for (e in entries) {
                var filePath = FileUtil.getSongFile(e).absolutePath
                if (!File(filePath).exists()) {
                    val ext = FileUtil.getExtension(filePath)
                    val base = FileUtil.getBaseName(filePath)
                    filePath = "$base.complete.$ext"
                }
                fw.write(
                    """
    $filePath
    
                    """.trimIndent()
                )
            }
        } catch (ignored: Exception) {
            Timber.w("Failed to save playlist: %s", name)
        } finally {
            bw.close()
            fw.close()
        }
    }

    override fun getRandomSongs(size: Int): MusicDirectory {
        val root = FileUtil.getMusicDirectory()
        val children: MutableList<File> = LinkedList()
        listFilesRecursively(root, children)
        val result = MusicDirectory()
        if (children.isEmpty()) {
            return result
        }
        val random = Random()
        for (i in 0 until size) {
            val file = children[random.nextInt(children.size)]
            result.addChild(createEntry(file, getName(file)))
        }
        return result
    }

    @Throws(Exception::class)
    override fun deletePlaylist(id: String) {
        throw OfflineException("Playlists not available in offline mode")
    }

    @Throws(Exception::class)
    override fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean) {
        throw OfflineException("Updating playlist not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getLyrics(artist: String, title: String): Lyrics? {
        throw OfflineException("Lyrics not available in offline mode")
    }

    @Throws(Exception::class)
    override fun scrobble(id: String, submission: Boolean) {
        throw OfflineException("Scrobbling not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getAlbumList(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        throw OfflineException("Album lists not available in offline mode")
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(ids: List<String>?): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun stopJukebox(): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun startJukebox(): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun setJukeboxGain(gain: Float): JukeboxStatus {
        throw OfflineException("Jukebox not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getStarred(): SearchResult {
        throw OfflineException("Starred not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getSongsByGenre(genre: String, count: Int, offset: Int): MusicDirectory {
        throw OfflineException("Getting Songs By Genre not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getGenres(refresh: Boolean): List<Genre>? {
        throw OfflineException("Getting Genres not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getUser(username: String): UserInfo {
        throw OfflineException("Getting user info not available in offline mode")
    }

    @Throws(Exception::class)
    override fun createShare(
        ids: List<String>,
        description: String?,
        expires: Long?
    ): List<Share> {
        throw OfflineException("Creating shares not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getShares(refresh: Boolean): List<Share> {
        throw OfflineException("Getting shares not available in offline mode")
    }

    @Throws(Exception::class)
    override fun deleteShare(id: String) {
        throw OfflineException("Deleting shares not available in offline mode")
    }

    @Throws(Exception::class)
    override fun updateShare(id: String, description: String?, expires: Long?) {
        throw OfflineException("Updating shares not available in offline mode")
    }

    @Throws(Exception::class)
    override fun star(id: String?, albumId: String?, artistId: String?) {
        throw OfflineException("Star not available in offline mode")
    }

    @Throws(Exception::class)
    override fun unstar(id: String?, albumId: String?, artistId: String?) {
        throw OfflineException("UnStar not available in offline mode")
    }

    @Throws(Exception::class)
    override fun getMusicFolders(refresh: Boolean): List<MusicFolder> {
        throw OfflineException("Music folders not available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): MusicDirectory {
        throw OfflineException("getAlbumList2 isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getVideoUrl(id: String): String? {
        throw OfflineException("getVideoUrl isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getChatMessages(since: Long?): List<ChatMessage?>? {
        throw OfflineException("getChatMessages isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun addChatMessage(message: String) {
        throw OfflineException("addChatMessage isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getBookmarks(): List<Bookmark?>? {
        throw OfflineException("getBookmarks isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun deleteBookmark(id: String) {
        throw OfflineException("deleteBookmark isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun createBookmark(id: String, position: Int) {
        throw OfflineException("createBookmark isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getVideos(refresh: Boolean): MusicDirectory? {
        throw OfflineException("getVideos isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getStarred2(): SearchResult {
        throw OfflineException("getStarred2 isn't available in offline mode")
    }

    override fun ping() {
        // Void
    }

    override fun isLicenseValid(): Boolean = true

    @Throws(OfflineException::class)
    override fun getArtists(refresh: Boolean): Indexes {
        throw OfflineException("getArtists isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getArtist(id: String, name: String?, refresh: Boolean): MusicDirectory {
        throw OfflineException("getArtist isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getAlbum(id: String, name: String?, refresh: Boolean): MusicDirectory {
        throw OfflineException("getAlbum isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory? {
        throw OfflineException("getPodcastEpisodes isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getDownloadInputStream(
        song: MusicDirectory.Entry,
        offset: Long,
        maxBitrate: Int
    ): Pair<InputStream, Boolean> {
        throw OfflineException("getDownloadInputStream isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun setRating(id: String, rating: Int) {
        throw OfflineException("setRating isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel> {
        throw OfflineException("getPodcastsChannels isn't available in offline mode")
    }

    companion object {
        private val COMPILE = Pattern.compile(" ")
        private fun getName(file: File): String? {
            var name = file.name
            if (file.isDirectory) {
                return name
            }
            if (name.endsWith(".partial") || name.contains(".partial.") ||
                name == Constants.ALBUM_ART_FILE
            ) {
                return null
            }
            name = name.replace(".complete", "")
            return FileUtil.getBaseName(name)
        }

        @Suppress("TooGenericExceptionCaught", "ComplexMethod", "LongMethod", "NestedBlockDepth")
        private fun createEntry(file: File, name: String?): MusicDirectory.Entry {
            val entry = MusicDirectory.Entry(file.path)
            entry.isDirectory = file.isDirectory
            entry.parent = file.parent
            entry.size = file.length()
            val root = FileUtil.getMusicDirectory().path
            entry.path = file.path.replaceFirst(
                String.format(Locale.ROOT, "^%s/", root).toRegex(), ""
            )
            entry.title = name
            if (file.isFile) {
                var artist: String? = null
                var album: String? = null
                var title: String? = null
                var track: String? = null
                var disc: String? = null
                var year: String? = null
                var genre: String? = null
                var duration: String? = null
                var hasVideo: String? = null
                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(file.path)
                    artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    mmr.release()
                } catch (ignored: Exception) {
                }
                entry.artist = artist ?: file.parentFile!!.parentFile!!.name
                entry.album = album ?: file.parentFile!!.name
                if (title != null) {
                    entry.title = title
                }
                entry.isVideo = hasVideo != null
                Timber.i("Offline Stuff: %s", track)
                if (track != null) {
                    var trackValue = 0
                    try {
                        val slashIndex = track.indexOf('/')
                        if (slashIndex > 0) {
                            track = track.substring(0, slashIndex)
                        }
                        trackValue = track.toInt()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Offline Stuff")
                    }
                    Timber.i("Offline Stuff: Setting Track: %d", trackValue)
                    entry.track = trackValue
                }
                if (disc != null) {
                    var discValue = 0
                    try {
                        val slashIndex = disc.indexOf('/')
                        if (slashIndex > 0) {
                            disc = disc.substring(0, slashIndex)
                        }
                        discValue = disc.toInt()
                    } catch (ignored: Exception) {
                    }
                    entry.discNumber = discValue
                }
                if (year != null) {
                    var yearValue = 0
                    try {
                        yearValue = year.toInt()
                    } catch (ignored: Exception) {
                    }
                    entry.year = yearValue
                }
                if (genre != null) {
                    entry.genre = genre
                }
                if (duration != null) {
                    var durationValue: Long = 0
                    try {
                        durationValue = duration.toLong()
                        durationValue = TimeUnit.MILLISECONDS.toSeconds(durationValue)
                    } catch (ignored: Exception) {
                    }
                    entry.setDuration(durationValue)
                }
            }
            entry.suffix = FileUtil.getExtension(file.name.replace(".complete", ""))
            val albumArt = FileUtil.getAlbumArtFile(entry)
            if (albumArt.exists()) {
                entry.coverArt = albumArt.path
            }
            return entry
        }

        @Suppress("NestedBlockDepth")
        private fun recursiveAlbumSearch(
            artistName: String,
            file: File,
            criteria: SearchCriteria,
            albums: MutableList<MusicDirectory.Entry>,
            songs: MutableList<MusicDirectory.Entry>
        ) {
            var closeness: Int
            for (albumFile in FileUtil.listMediaFiles(file)) {
                if (albumFile.isDirectory) {
                    val albumName = getName(albumFile)
                    if (matchCriteria(criteria, albumName).also { closeness = it } > 0) {
                        val album = createEntry(albumFile, albumName)
                        album.artist = artistName
                        album.closeness = closeness
                        albums.add(album)
                    }
                    for (songFile in FileUtil.listMediaFiles(albumFile)) {
                        val songName = getName(songFile)
                        if (songFile.isDirectory) {
                            recursiveAlbumSearch(artistName, songFile, criteria, albums, songs)
                        } else if (matchCriteria(criteria, songName).also { closeness = it } > 0) {
                            val song = createEntry(albumFile, songName)
                            song.artist = artistName
                            song.album = albumName
                            song.closeness = closeness
                            songs.add(song)
                        }
                    }
                } else {
                    val songName = getName(albumFile)
                    if (matchCriteria(criteria, songName).also { closeness = it } > 0) {
                        val song = createEntry(albumFile, songName)
                        song.artist = artistName
                        song.album = songName
                        song.closeness = closeness
                        songs.add(song)
                    }
                }
            }
        }

        private fun matchCriteria(criteria: SearchCriteria, name: String?): Int {
            val query = criteria.query.lowercase(Locale.ROOT)
            val queryParts = COMPILE.split(query)
            val nameParts = COMPILE.split(
                name!!.lowercase(Locale.ROOT)
            )
            var closeness = 0
            for (queryPart in queryParts) {
                for (namePart in nameParts) {
                    if (namePart == queryPart) {
                        closeness++
                    }
                }
            }
            return closeness
        }

        private fun listFilesRecursively(parent: File, children: MutableList<File>) {
            for (file in FileUtil.listMediaFiles(parent)) {
                if (file.isFile) {
                    children.add(file)
                } else {
                    listFilesRecursively(file, children)
                }
            }
        }
    }
}
