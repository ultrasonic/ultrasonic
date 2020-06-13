// Converts MusicDirectory entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIMusicDirectoryConverter")
package org.moire.ultrasonic.domain

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory as APIMusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

internal val dateFormat: DateFormat by lazy {
    SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
}

fun MusicDirectoryChild.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry().apply {
    id = this@toDomainEntity.id
    parent = this@toDomainEntity.parent
    isDirectory = this@toDomainEntity.isDir
    title = this@toDomainEntity.title
    album = this@toDomainEntity.album
    albumId = this@toDomainEntity.albumId
    artist = this@toDomainEntity.artist
    artistId = this@toDomainEntity.artistId
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
    isVideo = this@toDomainEntity.isVideo
    created = this@toDomainEntity.created?.time
    starred = this@toDomainEntity.starred != null
    discNumber = this@toDomainEntity.discNumber
    type = this@toDomainEntity.type
    if (this@toDomainEntity.streamId.isNotBlank()) {
        id = this@toDomainEntity.streamId
    }
    if (this@toDomainEntity.publishDate != null) {
        artist = dateFormat.format(this@toDomainEntity.publishDate!!.time)
    }
    userRating = this@toDomainEntity.userRating
    averageRating = this@toDomainEntity.averageRating
}

fun List<MusicDirectoryChild>.toDomainEntityList() = this.map { it.toDomainEntity() }

fun APIMusicDirectory.toDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.map { it.toDomainEntity() })
}
