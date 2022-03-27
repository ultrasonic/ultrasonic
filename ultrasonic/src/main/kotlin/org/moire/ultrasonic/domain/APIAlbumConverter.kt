// Converts Album entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIAlbumConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Album
typealias DomainAlbum = org.moire.ultrasonic.domain.Album

fun Album.toDomainEntity(): DomainAlbum = Album(
    id = this@toDomainEntity.id,
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

fun Album.toMusicDirectoryDomainEntity(): MusicDirectory = MusicDirectory().apply {
    addAll(this@toMusicDirectoryDomainEntity.songList.map { it.toTrackEntity() })
}

fun List<Album>.toDomainEntityList(): List<DomainAlbum> = this.map { it.toDomainEntity() }
