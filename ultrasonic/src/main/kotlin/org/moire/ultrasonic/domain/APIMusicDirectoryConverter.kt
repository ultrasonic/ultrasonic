/*
 * APIMusicDirectoryConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

@file:JvmName("APIMusicDirectoryConverter")
package org.moire.ultrasonic.domain

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory as APIMusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/*
 * Converts MusicDirectory entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
 * to app domain entities.
 *
 * Unlike other API endpoints getMusicDirectory doesn't return instances of Albums or Songs,
 * but just children, which can be albums or songs.
 */

internal val dateFormat: DateFormat by lazy {
    SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
}

fun MusicDirectoryChild.toTrackEntity(serverId: Int): Track = Track(id, serverId).apply {
    populateCommonProps(this, this@toTrackEntity)
    populateTrackProps(this, this@toTrackEntity)
}

fun MusicDirectoryChild.toAlbumEntity(serverId: Int): Album = Album(id, serverId).apply {
    populateCommonProps(this, this@toAlbumEntity)
}

private fun populateCommonProps(
    entry: MusicDirectory.Child,
    source: MusicDirectoryChild
) {
    entry.parent = source.parent
    entry.isDirectory = source.isDir
    entry.title = source.title
    entry.album = source.album
    entry.artist = source.artist
    entry.artistId = source.artistId
    entry.year = source.year
    entry.genre = source.genre
    entry.coverArt = source.coverArt
    entry.duration = source.duration
    entry.path = source.path
    entry.isVideo = source.isVideo
    entry.created = source.created?.time
    entry.starred = source.starred != null
    entry.discNumber = source.discNumber

    if (source.streamId.isNotBlank()) {
        entry.id = source.streamId
    }
    if (source.publishDate != null) {
        entry.artist = dateFormat.format(source.publishDate!!.time)
    }
}

private fun populateTrackProps(
    track: Track,
    source: MusicDirectoryChild
) {
    track.size = source.size
    track.contentType = source.contentType
    track.suffix = source.suffix
    track.transcodedContentType = source.transcodedContentType
    track.transcodedSuffix = source.transcodedSuffix
    track.track = source.track
    track.albumId = source.albumId
    track.bitRate = source.bitRate
    track.type = source.type
    track.userRating = source.userRating
    track.averageRating = source.averageRating
}

fun List<MusicDirectoryChild>.toDomainEntityList(serverId: Int): List<MusicDirectory.Child> {
    val newList: MutableList<MusicDirectory.Child> = mutableListOf()

    forEach {
        if (it.isDir)
            newList.add(it.toAlbumEntity(serverId))
        else
            newList.add(it.toTrackEntity(serverId))
    }

    return newList
}

fun List<MusicDirectoryChild>.toTrackList(serverId: Int): List<Track> = this.map {
    it.toTrackEntity(serverId)
}

fun APIMusicDirectory.toDomainEntity(serverId: Int): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.toDomainEntityList(serverId))
}
