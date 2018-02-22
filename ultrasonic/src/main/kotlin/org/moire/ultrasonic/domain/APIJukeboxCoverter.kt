// Collection of function to convert subsonic api jukebox responses to app entities
@file:JvmName("APIJukeboxConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.JukeboxStatus as ApiJukeboxStatus

fun ApiJukeboxStatus.toDomainEntity(): JukeboxStatus = JukeboxStatus(
    positionSeconds = this@toDomainEntity.position,
    currentPlayingIndex = this@toDomainEntity.currentIndex,
    isPlaying = this@toDomainEntity.playing,
    gain = this@toDomainEntity.gain
)
