package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class VideosResponse(
        status: Status,
        version: SubsonicAPIVersions,
        error: SubsonicError?) : SubsonicResponse(status, version, error) {
    @JsonProperty("videos") private val videosWrapper = VideosWrapper()

    val videosList: List<MusicDirectoryChild> get() = videosWrapper.videosList
}

internal class VideosWrapper(
        @JsonProperty("video") val videosList: List<MusicDirectoryChild> = emptyList())
