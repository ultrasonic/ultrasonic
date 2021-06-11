package org.moire.ultrasonic.api.subsonic

/**
 * Provides configuration for [SubsonicAPIClient].
 */
data class SubsonicClientConfiguration(
    val baseUrl: String,
    val username: String,
    val password: String,
    val minimalProtocolVersion: SubsonicAPIVersions,
    val clientID: String,
    val allowSelfSignedCertificate: Boolean = false,
    val enableLdapUserSupport: Boolean = false,
    val debug: Boolean = false,
    val isRealProtocolVersion: Boolean = false
)
