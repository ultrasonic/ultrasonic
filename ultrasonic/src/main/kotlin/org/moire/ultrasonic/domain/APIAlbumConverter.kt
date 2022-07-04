/*
 * APIAlbumConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts Album entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIAlbumConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Album
typealias DomainAlbum = org.moire.ultrasonic.domain.Album

fun Album.toDomainEntity(serverId: Int): DomainAlbum = Album(
    id = this@toDomainEntity.id,
    serverId = serverId,
    title = this@toDomainEntity.name ?: this@toDomainEntity.title,
    album = this@toDomainEntity.album,
    coverArt = this@toDomainEntity.coverArt,
    artist = this@toDomainEntity.artist,
    artistId = this@toDomainEntity.artistId,
    songCount = this@toDomainEntity.songCount.toLong(),
    duration = this@toDomainEntity.duration,
    created = this@toDomainEntity.created?.time,
    year = this@toDomainEntity.year,
    genre = this@toDomainEntity.genre,
    starred = this@toDomainEntity.starredDate.isNotEmpty()
)

fun Album.toMusicDirectoryDomainEntity(serverId: Int): MusicDirectory = MusicDirectory().apply {
    addAll(this@toMusicDirectoryDomainEntity.songList.map { it.toTrackEntity(serverId) })
}

fun List<Album>.toDomainEntityList(serverId: Int): List<DomainAlbum> = this.map {
    it.toDomainEntity(serverId)
}
