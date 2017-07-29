package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * Subsonic REST API versions.
 */
@JsonDeserialize(using = SubsonicAPIVersions.Companion.SubsonicAPIVersionsDeserializer::class)
enum class SubsonicAPIVersions(val subsonicVersions: String, val restApiVersion: String) {
    V1_1_0("3.8", "1.1.0"),
    V1_1_1("3.9", "1.1.1"),
    V1_2_0("4.0", "1.2.0"),
    V1_3_0("4.1", "1.3.0"),
    V1_4_0("4.2", "1.4.0"),
    V1_5_0("4.4", "1.5.0"),
    V1_6_0("4.5", "1.6.0"),
    V1_7_0("4.6", "1.7.0"),
    V1_8_0("4.7", "1.8.0"),
    V1_9_0("4.8", "1.9.0"),
    V1_10_2("4.9", "1.10.2"),
    V1_11_0("5.1", "1.11.0"),
    V1_12_0("5.2", "1.12.0"),
    V1_13_0("5.3", "1.13.0"),
    V1_14_0("6.0", "1.14.0"),
    V1_15_0("6.1", "1.15.0");

    companion object {
        @JvmStatic
        fun fromApiVersion(apiVersion: String): SubsonicAPIVersions {
            when (apiVersion) {
                "1.1.0" -> return V1_1_0
                "1.1.1" -> return V1_1_1
                "1.2.0" -> return V1_2_0
                "1.3.0" -> return V1_3_0
                "1.4.0" -> return V1_4_0
                "1.5.0" -> return V1_5_0
                "1.6.0" -> return V1_6_0
                "1.7.0" -> return V1_7_0
                "1.8.0" -> return V1_8_0
                "1.9.0" -> return V1_9_0
                "1.10.2" -> return V1_10_2
                "1.11.0" -> return V1_11_0
                "1.12.0" -> return V1_12_0
                "1.13.0" -> return V1_13_0
                "1.14.0" -> return V1_14_0
                "1.15.0" -> return V1_15_0
            }
            throw IllegalArgumentException("Unknown api version $apiVersion")
        }

        class SubsonicAPIVersionsDeserializer : JsonDeserializer<SubsonicAPIVersions>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): SubsonicAPIVersions {
                if (p.currentName != "version") {
                    throw JsonParseException(p, "Not valid token for API version!")
                }
                return fromApiVersion(p.text)
            }
        }
    }
}