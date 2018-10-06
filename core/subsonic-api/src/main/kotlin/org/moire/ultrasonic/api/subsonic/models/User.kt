package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class User(
    val username: String = "",
    val email: String = "",
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
    val shareRole: Boolean = false,
    val videoConverstionRole: Boolean = false,
    @JsonProperty("folder") val folderList: List<Int> = emptyList()
)
