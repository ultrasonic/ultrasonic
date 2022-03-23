/*
 * APIMusicDirectoryConverter.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
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

fun MusicDirectoryChild.toTrackEntity(): MusicDirectory.Track = MusicDirectory.Track(id).apply {
    populateCommonProps(this, this@toTrackEntity)
    populateTrackProps(this, this@toTrackEntity)
}

fun MusicDirectoryChild.toAlbumEntity(): MusicDirectory.Album = MusicDirectory.Album(id).apply {
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
    track: MusicDirectory.Track,
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

fun List<MusicDirectoryChild>.toDomainEntityList(): List<MusicDirectory.Child> {
    val newList: MutableList<MusicDirectory.Child> = mutableListOf()

    forEach {
        if (it.isDir)
            newList.add(it.toAlbumEntity())
        else
            newList.add(it.toTrackEntity())
    }

    return newList
}

fun List<MusicDirectoryChild>.toTrackList(): List<MusicDirectory.Track> = this.map {
    it.toTrackEntity()
}

fun APIMusicDirectory.toDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.toDomainEntityList())
}
