// Converts Indexes entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIIndexesConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Index as APIIndex
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes

fun APIIndexes.toArtistList(): List<Artist> {
    val list = this.shortcutList.map { it.toDomainEntity() }.toMutableList()
    list.addAll(this.indexList.foldIndexToArtistList())
    return list
}

fun APIIndexes.toIndexList(musicFolderId: String?): List<Index> {
    val list = this.shortcutList.map { it.toIndexEntity() }.toMutableList()
    list.addAll(this.indexList.foldIndexToIndexList(musicFolderId))
    return list
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
