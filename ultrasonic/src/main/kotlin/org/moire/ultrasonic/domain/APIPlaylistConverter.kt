/*
 * APIPlaylistConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts Playlist entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIPlaylistConverter")

package org.moire.ultrasonic.domain

import java.text.SimpleDateFormat
import kotlin.LazyThreadSafetyMode.NONE
import org.moire.ultrasonic.api.subsonic.models.Playlist as APIPlaylist
import org.moire.ultrasonic.util.Util.ifNotNull

internal val playlistDateFormat by lazy(NONE) { SimpleDateFormat.getInstance() }

fun APIPlaylist.toMusicDirectoryDomainEntity(serverId: Int): MusicDirectory =
    MusicDirectory().apply {
        name = this@toMusicDirectoryDomainEntity.name
        addAll(
            this@toMusicDirectoryDomainEntity.entriesList.map {
                val item = it.toTrackEntity(serverId)
                item.serverId = serverId
                item
            }
        )
    }

fun APIPlaylist.toDomainEntity(): Playlist = Playlist(
    this.id, this.name, this.owner,
    this.comment, this.songCount.toString(),
    this.created.ifNotNull { playlistDateFormat.format(it.time) } ?: "",
    public
)

fun List<APIPlaylist>.toDomainEntitiesList(): List<Playlist> = this.map { it.toDomainEntity() }
