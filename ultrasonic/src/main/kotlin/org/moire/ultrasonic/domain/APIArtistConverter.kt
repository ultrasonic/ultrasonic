/*
 * APIArtistConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts Artist entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIArtistConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Artist as APIArtist

// When we like to convert to an Artist
fun APIArtist.toDomainEntity(serverId: Int): Artist = Artist(
    id = this@toDomainEntity.id,
    serverId = serverId,
    coverArt = this@toDomainEntity.coverArt,
    name = this@toDomainEntity.name
)

// When we like to convert to an index (eg. a single directory).
fun APIArtist.toIndexEntity(serverId: Int): Index = Index(
    id = this@toIndexEntity.id,
    serverId = serverId,
    coverArt = this@toIndexEntity.coverArt,
    name = this@toIndexEntity.name
)

fun APIArtist.toMusicDirectoryDomainEntity(serverId: Int): MusicDirectory = MusicDirectory().apply {
    name = this@toMusicDirectoryDomainEntity.name
    addAll(this@toMusicDirectoryDomainEntity.albumsList.map { it.toDomainEntity(serverId) })
}

fun APIArtist.toDomainEntityList(serverId: Int): List<Album> {
    return this.albumsList.map { it.toDomainEntity(serverId) }
}
