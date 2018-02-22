package org.moire.ultrasonic.domain

enum class PlayerState {
    IDLE,
    DOWNLOADING,
    PREPARING,
    PREPARED,
    STARTED,
    STOPPED,
    PAUSED,
    COMPLETED
}
