/*
 * Album.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "albums", primaryKeys = ["id", "serverId"])
data class Album(
    override var id: String,
    @ColumnInfo(defaultValue = "-1")
    override var serverId: Int = -1,
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
