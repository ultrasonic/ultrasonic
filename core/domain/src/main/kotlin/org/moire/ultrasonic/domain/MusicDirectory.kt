package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

class MusicDirectory : ArrayList<MusicDirectory.Child>() {
    var name: String? = null

    fun addFirst(child: Child) {
        add(0, child)
    }

    fun addChild(child: Child) {
        add(child)
    }

    fun findChild(id: String): GenericEntry? = lastOrNull { it.id == id }

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

    fun getTracks(): List<Entry> {
        return mapNotNull {
            it as? Entry
        }
    }

    fun getAlbums(): List<Album> {
        return mapNotNull {
            it as? Album
        }
    }

    abstract class Child : Identifiable, GenericEntry() {
        abstract override var id: String
        abstract val parent: String?
        abstract val isDirectory: Boolean
        abstract var album: String?
        abstract val title: String?
        abstract override val name: String?
        abstract val discNumber: Int?
        abstract val coverArt: String?
        abstract val songCount: Long?
        abstract val created: Date?
        abstract var artist: String?
        abstract val artistId: String?
        abstract val duration: Int?
        abstract val year: Int?
        abstract val genre: String?
        abstract var starred: Boolean
        abstract val path: String?
        abstract var closeness: Int
    }

    // TODO: Rename to Track
    @Entity
    data class Entry(
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
        var isVideo: Boolean = false,
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

        fun compareTo(other: Entry): Int {
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

        override fun compareTo(other: Identifiable) = compareTo(other as Entry)
    }

    data class Album(
        @PrimaryKey override var id: String,
        override val parent: String? = null,
        override var album: String? = null,
        override val title: String? = null,
        override val name: String? = null,
        override val discNumber: Int = 0,
        override val coverArt: String? = null,
        override val songCount: Long? = null,
        override val created: Date? = null,
        override var artist: String? = null,
        override val artistId: String? = null,
        override val duration: Int = 0,
        override val year: Int = 0,
        override val genre: String? = null,
        override var starred: Boolean = false,
        override var path: String? = null,
        override var closeness: Int = 0,
    ) : Child() {
        override val isDirectory = true
    }
}
