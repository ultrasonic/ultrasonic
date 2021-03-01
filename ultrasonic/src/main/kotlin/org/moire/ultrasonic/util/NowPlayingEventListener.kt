package org.moire.ultrasonic.util

/**
 * Callback interface for Now Playing event subscribers
 */
interface NowPlayingEventListener {
    fun onDismissNowPlaying()
    fun onHideNowPlaying()
    fun onShowNowPlaying()
}
