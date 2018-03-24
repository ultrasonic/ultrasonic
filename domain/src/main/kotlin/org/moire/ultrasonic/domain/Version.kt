package org.moire.ultrasonic.domain

import net.swiftzer.semver.SemVer

/**
 * Represents the version number of the Subsonic Android app.
 */
data class Version(
    val version: SemVer
) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        return version.compareTo(other.version)
    }

    companion object {
        /**
         * Creates a new version instance by parsing the given string.
         *
         * @param version A string of the format "1.27", "1.27.2" or "1.27.beta3".
         */
        @JvmStatic
        fun fromCharSequence(version: String): Version {
            return Version(SemVer.parse(version))
        }
    }
}
