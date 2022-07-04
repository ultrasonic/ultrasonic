/*
 * APIBookmarkConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Contains helper functions to convert api Bookmark entity to domain entity
@file:JvmName("APIBookmarkConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Bookmark as ApiBookmark

fun ApiBookmark.toDomainEntity(serverId: Int): Bookmark = Bookmark(
    position = this@toDomainEntity.position.toInt(),
    username = this@toDomainEntity.username,
    comment = this@toDomainEntity.comment,
    created = this@toDomainEntity.created?.time,
    changed = this@toDomainEntity.changed?.time,
    track = this@toDomainEntity.entry.toTrackEntity(serverId)
)

fun List<ApiBookmark>.toDomainEntitiesList(serverId: Int): List<Bookmark> =
    map { it.toDomainEntity(serverId) }
