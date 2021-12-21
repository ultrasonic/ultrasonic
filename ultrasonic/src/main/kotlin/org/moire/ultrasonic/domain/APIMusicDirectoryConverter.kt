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

fun MusicDirectoryChild.toTrackEntity(): MusicDirectory.Entry = MusicDirectory.Entry(id).apply {
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
    entry: MusicDirectory.Entry,
    source: MusicDirectoryChild
) {
    entry.size = source.size
    entry.contentType = source.contentType
    entry.suffix = source.suffix
    entry.transcodedContentType = source.transcodedContentType
    entry.transcodedSuffix = source.transcodedSuffix
    entry.track = source.track
    entry.albumId = source.albumId
    entry.bitRate = source.bitRate
    entry.type = source.type
    entry.userRating = source.userRating
    entry.averageRating = source.averageRating
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

fun List<MusicDirectoryChild>.toTrackList(): List<MusicDirectory.Entry> = this.map {
    it.toTrackEntity()
}

fun APIMusicDirectory.toDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.toDomainEntityList())
}
