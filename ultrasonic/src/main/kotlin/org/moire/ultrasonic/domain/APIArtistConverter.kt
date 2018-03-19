// Converts Artist entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIArtistConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Artist as APIArtist

fun APIArtist.toDomainEntity(): Artist = Artist(
    id = this@toDomainEntity.id,
    name = this@toDomainEntity.name
)

fun APIArtist.toMusicDirectoryDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toMusicDirectoryDomainEntity.name
    addAll(this@toMusicDirectoryDomainEntity.albumsList.map { it.toDomainEntity() })
}
