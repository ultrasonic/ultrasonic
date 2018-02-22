package org.moire.ultrasonic.domain

import java.io.Serializable

class ChatMessage(
        val username: String,
        val time: Long,
        val message: String
) : Serializable {
    companion object {
        private const val serialVersionUID = 496544310289324167L
    }
}
