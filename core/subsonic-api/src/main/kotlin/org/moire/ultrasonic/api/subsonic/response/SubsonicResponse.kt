package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError

/**
 * Base Subsonic API response.
 */
@JsonRootName(value = "subsonic-response")
open class SubsonicResponse(
    val status: Status,
    val version: SubsonicAPIVersions,
    val error: SubsonicError?
) {
    @JsonDeserialize(using = Status.Companion.StatusJsonDeserializer::class)
    enum class Status(val jsonValue: String) {
        OK("ok"), ERROR("failed");

        companion object {
            fun getStatusFromJson(jsonValue: String) = values()
                .filter { it.jsonValue == jsonValue }.firstOrNull()
                ?: throw IllegalArgumentException("Unknown status value: $jsonValue")

            class StatusJsonDeserializer : JsonDeserializer<Status>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Status {
                    if (p.currentName != "status") {
                        throw JsonParseException(
                            p,
                            "Current token is not status. Current token name ${p.currentName}."
                        )
                    }
                    return getStatusFromJson(p.text)
                }
            }
        }
    }
}
