// Converts Album entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIAlbumConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Album

fun Album.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry(
    id = this@toDomainEntity.id,
    isDirectory = true,
    title = this@toDomainEntity.name,
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
    addAll(this@toMusicDirectoryDomainEntity.songList.map { it.toDomainEntity() })
}

fun List<Album>.toDomainEntityList(): List<MusicDirectory.Entry> = this.map { it.toDomainEntity() }
