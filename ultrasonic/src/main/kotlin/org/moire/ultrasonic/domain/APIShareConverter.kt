// Contains helper method to convert subsonic api share to domain model
@file:JvmName("APIShareConverter")
package org.moire.ultrasonic.domain

import java.text.SimpleDateFormat
import kotlin.LazyThreadSafetyMode.NONE
import org.moire.ultrasonic.api.subsonic.models.Share as APIShare

internal val shareTimeFormat by lazy(NONE) { SimpleDateFormat.getInstance() }

fun List<APIShare>.toDomainEntitiesList(): List<Share> = this.map {
    it.toDomainEntity()
}

fun APIShare.toDomainEntity(): Share = Share(
    created = this@toDomainEntity.created?.let { shareTimeFormat.format(it.time) },
    description = this@toDomainEntity.description,
    expires = this@toDomainEntity.expires?.let { shareTimeFormat.format(it.time) },
    id = this@toDomainEntity.id,
    lastVisited = this@toDomainEntity.lastVisited?.let { shareTimeFormat.format(it.time) },
    url = this@toDomainEntity.url,
    username = this@toDomainEntity.username,
    visitCount = this@toDomainEntity.visitCount.toLong(),
    entries = this@toDomainEntity.items.toDomainEntityList().toMutableList()
)
