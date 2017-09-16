// Converts Album entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIAlbumConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.domain.MusicDirectory

fun Album.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry().apply {
    id = this@toDomainEntity.id.toString()
    setIsDirectory(true)
    title = this@toDomainEntity.name
    coverArt = this@toDomainEntity.coverArt
    artist = this@toDomainEntity.artist
    artistId = this@toDomainEntity.artistId.toString()
    songCount = this@toDomainEntity.songCount.toLong()
    duration = this@toDomainEntity.duration
    created = this@toDomainEntity.created?.time
    year = this@toDomainEntity.year
    genre = this@toDomainEntity.genre
}

fun Album.toMusicDirectoryDomainEntity(): MusicDirectory = MusicDirectory().apply {
    addAll(this@toMusicDirectoryDomainEntity.songList.map { it.toDomainEntity() })
}

fun List<Album>.toDomainEntityList(): List<MusicDirectory.Entry> = this.map { it.toDomainEntity() }
