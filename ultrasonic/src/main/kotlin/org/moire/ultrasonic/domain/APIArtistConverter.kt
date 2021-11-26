// Converts Artist entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIArtistConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Artist as APIArtist

// When we like to convert to an Artist
fun APIArtist.toDomainEntity(): Artist = Artist(
    id = this@toDomainEntity.id,
    coverArt = this@toDomainEntity.coverArt,
    name = this@toDomainEntity.name
)

// When we like to convert to an index (eg. a single directory).
fun APIArtist.toIndexEntity(): Index = Index(
    id = this@toIndexEntity.id,
    coverArt = this@toIndexEntity.coverArt,
    name = this@toIndexEntity.name
)

fun APIArtist.toMusicDirectoryDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toMusicDirectoryDomainEntity.name
    addAll(this@toMusicDirectoryDomainEntity.albumsList.map { it.toDomainEntity() })
}

fun APIArtist.toDomainEntityList(): List<MusicDirectory.Album> {
    return this.albumsList.map { it.toDomainEntity() }
}
