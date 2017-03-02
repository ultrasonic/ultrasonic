package org.moire.ultrasonic.api.subsonic.models

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError

/**
 * Base Subsonic API response.
 */
data class SubsonicResponse(val status: Status,
                            val version: SubsonicAPIVersions,
                            val error: SubsonicError?) {
    enum class Status(val jsonValue: String) {
        OK("ok"), ERROR("failed");

        companion object {
            fun getStatusFromJson(jsonValue: String) = Status.values()
                    .filter { it.jsonValue == jsonValue }.firstOrNull()
                    ?: throw IllegalArgumentException("Unknown status value: $jsonValue")
        }
    }

    companion object {
        class ClassTypeAdapter: TypeAdapter<SubsonicResponse>() {
            override fun read(`in`: JsonReader?): SubsonicResponse {
                if (`in` == null) {
                    throw NullPointerException("No json for parsing")
                }

                var status: Status = Status.ERROR
                var version: SubsonicAPIVersions = SubsonicAPIVersions.V1_1_0
                var error: SubsonicError? = null
                `in`.beginObject()
                if ("subsonic-response" == `in`.nextName()) {
                    `in`.beginObject()
                    while (`in`.hasNext()) {
                        when (`in`.nextName()) {
                            "status" -> status = Status.getStatusFromJson(`in`.nextString())
                            "version" -> version = SubsonicAPIVersions.fromApiVersion(`in`.nextString())
                            "error" -> error = parseError(`in`)
                            else -> `in`.skipValue()
                        }
                    }
                    `in`.endObject()
                } else{
                    throw IllegalArgumentException("Not a subsonic-response json!")
                }
                `in`.endObject()
                return SubsonicResponse(status, version, error)
            }

            override fun write(out: JsonWriter?, value: SubsonicResponse?) {
                throw UnsupportedOperationException("not implemented")
            }

            private fun parseError(reader: JsonReader): SubsonicError? {
                var error: SubsonicError? = null

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "code" -> error = SubsonicError.parseErrorFromJson(reader.nextInt())
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                return error
            }
        }
    }
}