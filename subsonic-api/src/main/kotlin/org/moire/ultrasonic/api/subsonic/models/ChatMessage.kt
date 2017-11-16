package org.moire.ultrasonic.api.subsonic.models

data class ChatMessage(
        val username: String = "",
        val time: Long = 0,
        val message: String = "")
