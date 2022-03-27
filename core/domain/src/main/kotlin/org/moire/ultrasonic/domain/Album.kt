package org.moire.ultrasonic.domain

import androidx.room.PrimaryKey
import java.util.Date

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
) : MusicDirectory.Child() {
    override var isDirectory = true
    override var isVideo = false
}
