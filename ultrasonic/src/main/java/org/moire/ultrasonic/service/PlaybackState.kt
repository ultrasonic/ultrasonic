package org.moire.ultrasonic.service

import java.io.Serializable
import org.moire.ultrasonic.domain.Track

/**
 * Represents the state of the Media Player implementation
 */
data class PlaybackState(
    val songs: List<Track> = listOf(),
    val currentPlayingIndex: Int = 0,
    val currentPlayingPosition: Int = 0,
    var shufflePlay: Boolean = false,
    var repeatMode: Int = 0
) : Serializable {
    companion object {
        const val serialVersionUID = -293487987L
    }
}
