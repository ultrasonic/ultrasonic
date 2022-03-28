// Contains helper functions to convert api Bookmark entity to domain entity
@file:JvmName("APIBookmarkConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Bookmark as ApiBookmark

fun ApiBookmark.toDomainEntity(): Bookmark = Bookmark(
    position = this@toDomainEntity.position.toInt(),
    username = this@toDomainEntity.username,
    comment = this@toDomainEntity.comment,
    created = this@toDomainEntity.created?.time,
    changed = this@toDomainEntity.changed?.time,
    track = this@toDomainEntity.entry.toTrackEntity()
)

fun List<ApiBookmark>.toDomainEntitiesList(): List<Bookmark> = map { it.toDomainEntity() }
