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
import org.moire.ultrasonic.util.StorageFile
import java.io.InputStream
import java.io.Reader
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
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
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber
import java.io.File
import java.io.FileReader
import java.io.FileWriter

@Suppress("TooManyFunctions")
class OfflineMusicService : MusicService, KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    override fun getIndexes(musicFolderId: String?, refresh: Boolean): List<Index> {
        val indexes: MutableList<Index> = ArrayList()
        val root = FileUtil.musicDirectory
        for (file in FileUtil.listFiles(root)) {
            if (file.isDirectory) {
                val index = Index(file.path)
                index.id = file.path
                index.index = file.name.substring(0, 1)
                index.name = file.name
                indexes.add(index)
            }
        }
        val ignoredArticlesString = "The El La Los Las Le Les"
        val ignoredArticles = COMPILE.split(ignoredArticlesString)
        indexes.sortWith { lhsArtist, rhsArtist ->
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

        return indexes
    }

    /*
    * Especially when dealing with indexes, this method can return Albums, Entries or a mix of both!
    */
    override fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val dir = StorageFile.getFromPath(id)
        val result = MusicDirectory()
        result.name = dir?.name ?: return result

        val seen: MutableCollection<String?> = HashSet()

        for (file in FileUtil.listMediaFiles(dir)) {
            val filename = getName(file.name, file.isDirectory)
            if (filename != null && !seen.contains(filename)) {
                seen.add(filename)
                if (file.isFile) {
                    result.add(createEntry(file, filename))
                } else {
                    result.add(createAlbum(file, filename))
                }
            }
        }

        return result
    }

    override fun search(criteria: SearchCriteria): SearchResult {
        val artists: MutableList<ArtistOrIndex> = ArrayList()
        val albums: MutableList<MusicDirectory.Album> = ArrayList()
        val songs: MutableList<MusicDirectory.Entry> = ArrayList()
        val root = FileUtil.musicDirectory
        var closeness: Int
        for (artistFile in FileUtil.listFiles(root)) {
            val artistName = artistFile.name
            if (artistFile.isDirectory) {
                if (matchCriteria(criteria, artistName).also { closeness = it } > 0) {
                    val artist = Index(artistFile.path)
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
                val entryFile = StorageFile.getFromPath(line) ?: continue
                val entryName = getName(entryFile.name, entryFile.isDirectory)
                if (entryName != null) {
                    playlist.add(createEntry(entryFile, entryName))
                }
            }
            playlist
        } finally {
            buffer.safeClose()
            reader.safeClose()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    @Throws(Exception::class)
    override fun createPlaylist(id: String?, name: String?, entries: List<MusicDirectory.Entry>) {
        val playlistFile =
            FileUtil.getPlaylistFile(activeServerProvider.getActiveServer().name, name)
        val fw = FileWriter(playlistFile)
        val bw = BufferedWriter(fw)
        try {
            fw.write("#EXTM3U\n")
            for (e in entries) {
                var filePath = FileUtil.getSongFile(e)
                if (!StorageFile.isPathExists(filePath)) {
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
        val root = FileUtil.musicDirectory
        val children: MutableList<StorageFile> = LinkedList()
        listFilesRecursively(root, children)
        val result = MusicDirectory()
        if (children.isEmpty()) {
            return result
        }
        children.shuffle()
        val finalSize: Int = children.size.coerceAtMost(size)
        for (i in 0 until finalSize) {
            val file = children[i % children.size]
            result.add(createEntry(file, getName(file.name, file.isDirectory)))
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
    ): List<MusicDirectory.Album> {
        throw OfflineException("Album lists not available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getAlbumList2(
        type: String,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): List<MusicDirectory.Album> {
        throw OfflineException("getAlbumList2 isn't available in offline mode")
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
    override fun getBookmarks(): List<Bookmark> {
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
    override fun getArtists(refresh: Boolean): List<Artist> {
        throw OfflineException("getArtists isn't available in offline mode")
    }

    @Throws(OfflineException::class)
    override fun getArtist(id: String, name: String?, refresh: Boolean):
        List<MusicDirectory.Album> {
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
        maxBitrate: Int,
        save: Boolean
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

    private fun getName(fileName: String, isDirectory: Boolean): String? {
        if (isDirectory) {
            return fileName
        }
        if (fileName.endsWith(".partial") || fileName.contains(".partial.") ||
            fileName == Constants.ALBUM_ART_FILE
        ) {
            return null
        }
        val name = fileName.replace(".complete", "")
        return FileUtil.getBaseName(name)
    }


    private fun createEntry(file: StorageFile, name: String?): MusicDirectory.Entry {
        val entry = MusicDirectory.Entry(file.path)
        entry.populateWithDataFrom(file, name)
        return entry
    }

    private fun createAlbum(file: StorageFile, name: String?): MusicDirectory.Album {
        val album = MusicDirectory.Album(file.path)
        album.populateWithDataFrom(file, name)
        return album
    }

    /*
     * Extracts some basic data from a File object and applies it to an Album or Entry
     */
    private fun MusicDirectory.Child.populateWithDataFrom(file: StorageFile, name: String?) {
        isDirectory = file.isDirectory
        parent = file.parent!!.path
        val root = FileUtil.musicDirectory.path
        path = file.path.replaceFirst(
            String.format(Locale.ROOT, "^%s/", root).toRegex(), ""
        )
        title = name

        val albumArt = FileUtil.getAlbumArtFile(this)
        if (albumArt != null && File(albumArt).exists()) {
            coverArt = albumArt
        }
    }

    /*
     * More extensive variant of Child.populateWithDataFrom(), which also parses the ID3 tags of
     * a given track file.
     */
    private fun MusicDirectory.Entry.populateWithDataFrom(file: StorageFile, name: String?) {
        (this as MusicDirectory.Child).populateWithDataFrom(file, name)

        val meta = RawMetadata(null)

        try {
            val mmr = MediaMetadataRetriever()

            val descriptor = file.getDocumentFileDescriptor("r")!!
            mmr.setDataSource(descriptor.fileDescriptor)
            descriptor.close()

            meta.artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            meta.album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            meta.title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            meta.track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            meta.disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            meta.year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            meta.genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            meta.duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            meta.hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            mmr.release()
        } catch (ignored: Exception) {
        }

        artist = meta.artist ?: file.parent!!.parent!!.name
        album = meta.album ?: file.parent!!.name
        title = meta.title ?: title
        isVideo = meta.hasVideo != null
        track = parseSlashedNumber(meta.track)
        discNumber = parseSlashedNumber(meta.disc)
        year = meta.year?.toIntOrNull()
        genre = meta.genre
        duration = parseDuration(meta.duration)
        size = if (file.isFile) file.length else 0
        suffix = FileUtil.getExtension(file.name.replace(".complete", ""))
    }

    /*
     * Parses a number from a string in the format of 05/21,
     * where the first number is the track number
     * and the second the number of total tracks
     */
    private fun parseSlashedNumber(string: String?): Int? {
        if (string == null) return null

        val slashIndex = string.indexOf('/')
        if (slashIndex > 0)
            return string.substring(0, slashIndex).toIntOrNull()
        else
            return string.toIntOrNull()
    }

    /*
     * Parses a duration from a String
     */
    private fun parseDuration(string: String?): Int? {
        if (string == null) return null

        val duration: Long? = string.toLongOrNull()

        if (duration != null)
            return TimeUnit.MILLISECONDS.toSeconds(duration).toInt()
        else
            return null
    }

    // TODO: Simplify this deeply nested and complicated function
    @Suppress("NestedBlockDepth")
    private fun recursiveAlbumSearch(
        artistName: String,
        file: StorageFile,
        criteria: SearchCriteria,
        albums: MutableList<MusicDirectory.Album>,
        songs: MutableList<MusicDirectory.Entry>
    ) {
        var closeness: Int
        for (albumFile in FileUtil.listMediaFiles(file)) {
            if (albumFile.isDirectory) {
                val albumName = getName(albumFile.name, albumFile.isDirectory)
                if (matchCriteria(criteria, albumName).also { closeness = it } > 0) {
                    val album = createAlbum(albumFile, albumName)
                    album.artist = artistName
                    album.closeness = closeness
                    albums.add(album)
                }
                for (songFile in FileUtil.listMediaFiles(albumFile)) {
                    val songName = getName(songFile.name, songFile.isDirectory)
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
                val songName = getName(albumFile.name, albumFile.isDirectory)
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

    private fun listFilesRecursively(parent: StorageFile, children: MutableList<StorageFile>) {
        for (file in FileUtil.listMediaFiles(parent)) {
            if (file.isFile) {
                children.add(file)
            } else {
                listFilesRecursively(file, children)
            }
        }
    }

    data class RawMetadata(val id: String?) {
        var artist: String? = null
        var album: String? = null
        var title: String? = null
        var track: String? = null
        var disc: String? = null
        var year: String? = null
        var genre: String? = null
        var duration: String? = null
        var hasVideo: String? = null
    }

    companion object {
        private val COMPILE = Pattern.compile(" ")
    }
}
