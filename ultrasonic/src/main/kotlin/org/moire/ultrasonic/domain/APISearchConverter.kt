/*
 * APISearchConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts SearchResult entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APISearchConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.SearchResult as APISearchResult
import org.moire.ultrasonic.api.subsonic.models.SearchThreeResult
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

fun APISearchResult.toDomainEntity(serverId: Int): SearchResult = SearchResult(
    emptyList(), emptyList(),
    this.matchList.map { it.toTrackEntity(serverId) }
)

fun SearchTwoResult.toDomainEntity(serverId: Int): SearchResult = SearchResult(
    this.artistList.map { it.toIndexEntity(serverId) },
    this.albumList.map { it.toDomainEntity(serverId) },
    this.songList.map { it.toTrackEntity(serverId) }
)

fun SearchThreeResult.toDomainEntity(serverId: Int): SearchResult = SearchResult(
    this.artistList.map { it.toDomainEntity(serverId) },
    this.albumList.map { it.toDomainEntity(serverId) },
    this.songList.map { it.toTrackEntity(serverId) }
)
