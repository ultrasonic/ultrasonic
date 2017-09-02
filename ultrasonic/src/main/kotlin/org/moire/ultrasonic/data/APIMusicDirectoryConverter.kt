// Converts MusicDirectory entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIMusicDirectoryConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory as APIMusicDirectory

fun MusicDirectoryChild.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry().apply {
    id = this@toDomainEntity.id.toString()
    parent = this@toDomainEntity.parent.toString()
    setIsDirectory(this@toDomainEntity.isDir)
    title = this@toDomainEntity.title
    album = this@toDomainEntity.album
    albumId = this@toDomainEntity.albumId.toString()
    artist = this@toDomainEntity.artist
    artistId = this@toDomainEntity.artistId.toString()
    track = this@toDomainEntity.track
    year = this@toDomainEntity.year
    genre = this@toDomainEntity.genre
    contentType = this@toDomainEntity.contentType
    suffix = this@toDomainEntity.suffix
    transcodedContentType = this@toDomainEntity.transcodedContentType
    transcodedSuffix = this@toDomainEntity.transcodedSuffix
    coverArt = this@toDomainEntity.coverArt
    size = this@toDomainEntity.size
    duration = this@toDomainEntity.duration
    bitRate = this@toDomainEntity.bitRate
    path = this@toDomainEntity.path
    setIsVideo(this@toDomainEntity.isVideo)
    created = this@toDomainEntity.created?.time
    starred = this@toDomainEntity.starred != null
    discNumber = this@toDomainEntity.discNumber
    type = this@toDomainEntity.type
}

fun APIMusicDirectory.toDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.map { it.toDomainEntity() })
}
