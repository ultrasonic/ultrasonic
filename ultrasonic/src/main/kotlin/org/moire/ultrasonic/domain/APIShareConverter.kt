/*
 * APIShareConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Contains helper method to convert subsonic api share to domain model
@file:JvmName("APIShareConverter")
package org.moire.ultrasonic.domain

import java.text.SimpleDateFormat
import kotlin.LazyThreadSafetyMode.NONE
import org.moire.ultrasonic.api.subsonic.models.Share as APIShare
import org.moire.ultrasonic.util.Util.ifNotNull

internal val shareTimeFormat by lazy(NONE) { SimpleDateFormat.getInstance() }

fun List<APIShare>.toDomainEntitiesList(serverId: Int): List<Share> = this.map {
    it.toDomainEntity(serverId)
}

fun APIShare.toDomainEntity(serverId: Int): Share = Share(
    created = this@toDomainEntity.created.ifNotNull { shareTimeFormat.format(it.time) },
    description = this@toDomainEntity.description,
    expires = this@toDomainEntity.expires.ifNotNull { shareTimeFormat.format(it.time) },
    id = this@toDomainEntity.id,
    lastVisited = this@toDomainEntity.lastVisited.ifNotNull { shareTimeFormat.format(it.time) },
    url = this@toDomainEntity.url,
    username = this@toDomainEntity.username,
    visitCount = this@toDomainEntity.visitCount.toLong(),
    tracks = this@toDomainEntity.items.toTrackList(serverId).toMutableList()
)
