package org.moire.ultrasonic.api.subsonic.models

/**
 * Supported jukebox commands.
 *
 * It is used in [org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition.jukeboxControl] call.
 */
enum class JukeboxAction(val action: String) {
    GET("get"),
    STATUS("status"),
    SET("set"),
    START("start"),
    STOP("stop"),
    SKIP("skip"),
    ADD("add"),
    CLEAR("clear"),
    REMOVE("remove"),
    SHUFFLE("shuffle"),
    SET_GAIN("setGain");

    override fun toString(): String {
        return action
    }
}
