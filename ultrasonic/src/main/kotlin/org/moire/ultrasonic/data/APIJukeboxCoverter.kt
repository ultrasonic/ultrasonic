// Collection of function to convert subsonic api jukebox responses to app entities
@file:JvmName("APIJukeboxConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.api.subsonic.models.JukeboxStatus as ApiJukeboxStatus

fun ApiJukeboxStatus.toDomainEntity(): JukeboxStatus = JukeboxStatus().apply {
    positionSeconds = this@toDomainEntity.position
    setCurrentIndex(this@toDomainEntity.currentIndex)
    isPlaying = this@toDomainEntity.playing
    gain = this@toDomainEntity.gain
}
