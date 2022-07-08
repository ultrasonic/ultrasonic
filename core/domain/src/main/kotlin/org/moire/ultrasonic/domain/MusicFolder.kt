/*
 * MusicFolder.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Represents a top level directory in which music or other media is stored.
 */
@Entity(tableName = "music_folders", primaryKeys = ["id", "serverId"])
data class MusicFolder(
    override val id: String,
    override val name: String,
    @ColumnInfo(defaultValue = "-1")
    var serverId: Int
) : GenericEntry()
