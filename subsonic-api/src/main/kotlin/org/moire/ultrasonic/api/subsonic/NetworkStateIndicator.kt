package org.moire.ultrasonic.api.subsonic

/**
 * Provides info about device current connection state.
 *
 * Default implementation always assumes that device is online.
 */
interface NetworkStateIndicator {
    /**
     * Get current device connection state.
     */
    fun isOnline() = true
}
