package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

class MusicDirectory : ArrayList<MusicDirectory.Child>() {
    var name: String? = null

    @JvmOverloads
    fun getChildren(
        includeDirs: Boolean = true,
        includeFiles: Boolean = true
    ): List<Child> {
        if (includeDirs && includeFiles) {
            return toList()
        }

        return filter { it.isDirectory && includeDirs || !it.isDirectory && includeFiles }
    }

    fun getTracks(): List<Track> {
        return mapNotNull {
            it as? Track
        }
    }

    fun getAlbums(): List<Album> {
        return mapNotNull {
            it as? Album
        }
    }

    abstract class Child : GenericEntry() {
        abstract override var id: String
        abstract var parent: String?
        abstract var isDirectory: Boolean
        abstract var album: String?
        abstract var title: String?
        abstract override val name: String?
        abstract var discNumber: Int?
        abstract var coverArt: String?
        abstract var songCount: Long?
        abstract var created: Date?
        abstract var artist: String?
        abstract var artistId: String?
        abstract var duration: Int?
        abstract var year: Int?
        abstract var genre: String?
        abstract var starred: Boolean
        abstract var path: String?
        abstract var closeness: Int
        abstract var isVideo: Boolean
    }

    @Entity
    data class Track(
        @PrimaryKey override var id: String,
        override var parent: String? = null,
        override var isDirectory: Boolean = false,
        override var title: String? = null,
        override var album: String? = null,
        var albumId: String? = null,
        override var artist: String? = null,
        override var artistId: String? = null,
        var track: Int? = null,
        override var year: Int? = null,
        override var genre: String? = null,
        var contentType: String? = null,
        var suffix: String? = null,
        var transcodedContentType: String? = null,
        var transcodedSuffix: String? = null,
        override var coverArt: String? = null,
        var size: Long? = null,
        override var songCount: Long? = null,
        override var duration: Int? = null,
        var bitRate: Int? = null,
        override var path: String? = null,
        override var isVideo: Boolean = false,
        override var starred: Boolean = false,
        override var discNumber: Int? = null,
        var type: String? = null,
        override var created: Date? = null,
        override var closeness: Int = 0,
        var bookmarkPosition: Int = 0,
        var userRating: Int? = null,
        var averageRating: Float? = null,
        override var name: String? = null
    ) : Serializable, Child() {
        fun setDuration(duration: Long) {
            this.duration = duration.toInt()
        }

        companion object {
            private const val serialVersionUID = -3339106650010798108L
        }

        fun compareTo(other: Track): Int {
            when {
                this.closeness == other.closeness -> {
                    return 0
                }
                this.closeness > other.closeness -> {
                    return -1
                }
                else -> {
                    return 1
                }
            }
        }

        override fun compareTo(other: Identifiable) = compareTo(other as Track)
    }

    data class Album(
        @PrimaryKey override var id: String,
        override var parent: String? = null,
        override var album: String? = null,
        override var title: String? = null,
        override val name: String? = null,
        override var discNumber: Int? = 0,
        override var coverArt: String? = null,
        override var songCount: Long? = null,
        override var created: Date? = null,
        override var artist: String? = null,
        override var artistId: String? = null,
        override var duration: Int? = 0,
        override var year: Int? = 0,
        override var genre: String? = null,
        override var starred: Boolean = false,
        override var path: String? = null,
        override var closeness: Int = 0,
    ) : Child() {
        override var isDirectory = true
        override var isVideo = false
    }
}
