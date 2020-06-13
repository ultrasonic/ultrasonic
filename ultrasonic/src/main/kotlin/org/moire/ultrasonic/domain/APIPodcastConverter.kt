// Converts podcasts entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIPodcastConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.PodcastChannel

fun PodcastChannel.toDomainEntity(): PodcastsChannel = PodcastsChannel(
    this.id, this.title, this.url, this.description, this.status
)

fun List<PodcastChannel>.toDomainEntitiesList(): List<PodcastsChannel> = this
    .map { it.toDomainEntity() }
