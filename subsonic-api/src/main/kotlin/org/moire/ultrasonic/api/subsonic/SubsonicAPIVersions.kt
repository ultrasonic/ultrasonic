package org.moire.ultrasonic.api.subsonic

/**
 * Subsonic REST API versions.
 */
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
    V1_14_0("6.0", "1.14.0");

    companion object {
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
            }
            throw IllegalArgumentException("Unknown api version $apiVersion")
        }
    }
}