/*
 * APIIndexesConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts Indexes entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIIndexesConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Index as APIIndex
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes

fun APIIndexes.toArtistList(serverId: Int): List<Artist> {
    val shortcuts = this.shortcutList.map { it.toDomainEntity(serverId) }.toMutableList()
    val indexes = this.indexList.foldIndexToArtistList(serverId)

    indexes.forEach {
        if (!shortcuts.contains(it)) {
            shortcuts.add(it)
        }
    }

    return shortcuts
}

fun APIIndexes.toIndexList(serverId: Int, musicFolderId: String?): List<Index> {
    val shortcuts = this.shortcutList.map { it.toIndexEntity(serverId) }.toMutableList()
    val indexes = this.indexList.foldIndexToIndexList(musicFolderId, serverId)

    indexes.forEach {
        if (!shortcuts.contains(it)) {
            shortcuts.add(it)
        }
    }

    return shortcuts
}

private fun List<APIIndex>.foldIndexToArtistList(serverId: Int): List<Artist> = this.fold(
    listOf()
) { acc, index ->
    acc + index.artists.map {
        it.toDomainEntity(serverId)
    }
}

private fun List<APIIndex>.foldIndexToIndexList(
    musicFolderId: String?,
    serverId: Int
): List<Index> = this.fold(
    listOf()
) { acc, index ->
    acc + index.artists.map {
        val ret = it.toIndexEntity(serverId)
        ret.musicFolderId = musicFolderId
        ret
    }
}
