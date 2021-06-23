// Converts Indexes entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIIndexesConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Index as APIIndex
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes

fun APIIndexes.toArtistList(): List<Artist> {
    val shortcuts = this.shortcutList.map { it.toDomainEntity() }.toMutableList()
    val indexes = this.indexList.foldIndexToArtistList()

    indexes.forEach {
        if (!shortcuts.contains(it)) {
            shortcuts.add(it)
        }
    }

    return shortcuts
}

fun APIIndexes.toIndexList(musicFolderId: String?): List<Index> {
    val shortcuts = this.shortcutList.map { it.toIndexEntity() }.toMutableList()
    val indexes = this.indexList.foldIndexToIndexList(musicFolderId)

    indexes.forEach {
        if (!shortcuts.contains(it)) {
            shortcuts.add(it)
        }
    }

    return shortcuts
}

private fun List<APIIndex>.foldIndexToArtistList(): List<Artist> = this.fold(
    listOf(),
    { acc, index ->
        acc + index.artists.map {
            it.toDomainEntity()
        }
    }
)

private fun List<APIIndex>.foldIndexToIndexList(musicFolderId: String?): List<Index> = this.fold(
    listOf(),
    { acc, index ->
        acc + index.artists.map {
            val ret = it.toIndexEntity()
            ret.musicFolderId = musicFolderId
            ret
        }
    }
)
