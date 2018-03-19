package org.moire.ultrasonic.domain

data class JukeboxStatus(
        var positionSeconds: Int? = null,
        var currentPlayingIndex: Int? = null,
        var gain: Float? = null,
        var isPlaying: Boolean = false
)
