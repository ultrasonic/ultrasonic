package org.moire.ultrasonic.domain

/**
 * Information about user
 */
data class UserInfo(
    val userName: String? = null,
    val email: String? = null,
    val scrobblingEnabled: Boolean = false,
    val adminRole: Boolean = false,
    val settingsRole: Boolean = false,
    val downloadRole: Boolean = false,
    val uploadRole: Boolean = false,
    val playlistRole: Boolean = false,
    val coverArtRole: Boolean = false,
    val commentRole: Boolean = false,
    val podcastRole: Boolean = false,
    val streamRole: Boolean = false,
    val jukeboxRole: Boolean = false,
    val shareRole: Boolean = false
)
