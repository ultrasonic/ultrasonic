// Converts Indexes entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIIndexesConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes

fun APIIndexes.toDomainEntity(): Indexes = Indexes(
    this.lastModified, this.ignoredArticles,
    this.shortcutList.map { it.toDomainEntity() }.toMutableList(),
    this.indexList.foldIndexToArtistList().toMutableList()
)

private fun List<Index>.foldIndexToArtistList(): List<Artist> = this.fold(
    listOf(), { acc, index -> acc + index.artists.map { it.toDomainEntity() } }
)
