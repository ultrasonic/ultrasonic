package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.ChatMessage

class ChatMessagesResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("chatMessages") private val wrapper = ChatMessagesWrapper()

    val chatMessages: List<ChatMessage> get() = wrapper.messagesList
}

internal class ChatMessagesWrapper(
    @JsonProperty("chatMessage") val messagesList: List<ChatMessage> = emptyList()
)
