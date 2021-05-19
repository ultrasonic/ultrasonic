package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.lang.NumberFormatException

/**
 * Subsonic REST API versions.
 */
@Suppress("MagicNumber", "ComplexMethod", "ReturnCount", "ThrowsCount")
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
    V1_15_0("6.1", "1.15.0"),
    V1_16_0("6.1.2", "1.16.0");

    companion object {
        @JvmStatic @Throws(IllegalArgumentException::class)
        fun getClosestKnownClientApiVersion(apiVersion: String): SubsonicAPIVersions {
            val versionComponents = apiVersion.split(".")
            if (versionComponents.size < 2)
                throw IllegalArgumentException("Unknown api version $apiVersion")

            try {
                val majorVersion = versionComponents[0].toInt()
                val minorVersion = versionComponents[1].toInt()
                val patchVersion = if (versionComponents.size > 2) versionComponents[2].toInt()
                else 0

                when (majorVersion) {
                    1 -> when {
                        minorVersion < 1 ->
                            throw IllegalArgumentException("Unknown api version $apiVersion")
                        minorVersion < 2 && patchVersion < 1 -> return V1_1_0
                        minorVersion < 2 -> return V1_1_1
                        minorVersion < 3 -> return V1_2_0
                        minorVersion < 4 -> return V1_3_0
                        minorVersion < 5 -> return V1_4_0
                        minorVersion < 6 -> return V1_5_0
                        minorVersion < 7 -> return V1_6_0
                        minorVersion < 8 -> return V1_7_0
                        minorVersion < 9 -> return V1_8_0
                        minorVersion < 10 -> return V1_9_0
                        minorVersion < 11 -> return V1_10_2
                        minorVersion < 12 -> return V1_11_0
                        minorVersion < 13 -> return V1_12_0
                        minorVersion < 14 -> return V1_13_0
                        minorVersion < 15 -> return V1_14_0
                        minorVersion < 16 -> return V1_15_0
                        else -> return V1_16_0
                    }
                    // Subsonic API specifies that the client's and the server's major API version
                    // must be the same
                    else -> throw IllegalArgumentException("Unknown api version $apiVersion")
                }
            } catch (exception: NumberFormatException) {
                throw IllegalArgumentException("Malformed api version $apiVersion")
            }
        }

        class SubsonicAPIVersionsDeserializer : JsonDeserializer<SubsonicAPIVersions>() {
            override fun deserialize(
                p: JsonParser,
                ctxt: DeserializationContext?
            ): SubsonicAPIVersions {
                if (p.currentName != "version") {
                    throw JsonParseException(p, "Not valid token for API version!")
                }
                return getClosestKnownClientApiVersion(p.text)
            }
        }
    }
}
