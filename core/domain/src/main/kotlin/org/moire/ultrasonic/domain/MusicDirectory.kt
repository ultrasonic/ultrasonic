package org.moire.ultrasonic.domain

import java.io.Serializable
import java.util.Date

class MusicDirectory {
    var name: String? = null
    private val children = mutableListOf<Entry>()

    fun addAll(entries: Collection<Entry>) {
        children.addAll(entries)
    }

    fun addFirst(child: Entry) {
        children.add(0, child)
    }

    fun addChild(child: Entry) {
        children.add(child)
    }

    fun findChild(id: String): Entry? = children.lastOrNull { it.id == id }

    fun getAllChild(): List<Entry> = children.toList()

    @JvmOverloads
    fun getChildren(
        includeDirs: Boolean = true,
        includeFiles: Boolean = true
    ): List<Entry> {
        if (includeDirs && includeFiles) {
            return children
        }

        return children.filter { it.isDirectory && includeDirs || !it.isDirectory && includeFiles }
    }

    data class Entry(
        override var id: String? = null,
        var parent: String? = null,
        var isDirectory: Boolean = false,
        var title: String? = null,
        var album: String? = null,
        var albumId: String? = null,
        var artist: String? = null,
        var artistId: String? = null,
        var track: Int? = 0,
        var year: Int? = 0,
        var genre: String? = null,
        var contentType: String? = null,
        var suffix: String? = null,
        var transcodedContentType: String? = null,
        var transcodedSuffix: String? = null,
        var coverArt: String? = null,
        var size: Long? = null,
        var songCount: Long? = null,
        var duration: Int? = null,
        var bitRate: Int? = null,
        var path: String? = null,
        var isVideo: Boolean = false,
        var starred: Boolean = false,
        var discNumber: Int? = null,
        var type: String? = null,
        var created: Date? = null,
        var closeness: Int = 0,
        var bookmarkPosition: Int = 0,
        var userRating: Int? = null,
        var averageRating: Float? = null
    ) : Serializable, GenericEntry() {
        fun setDuration(duration: Long) {
            this.duration = duration.toInt()
        }

        companion object {
            private const val serialVersionUID = -3339106650010798108L
        }
    }
}
