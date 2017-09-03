package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class PodcastChannel(
        val id: Long = -1,
        val url: String = "",
        val title: String = "",
        val description: String = "",
        val coverArt: String = "",
        val originalImageUrl: String = "",
        val status: String = "",
        val errorMessage: String = "",
        @JsonProperty("episode") val episodeList: List<MusicDirectoryChild> = emptyList())
