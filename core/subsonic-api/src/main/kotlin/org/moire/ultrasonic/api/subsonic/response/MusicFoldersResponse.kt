package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicFolder

class MusicFoldersResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("musicFolders") private val wrapper = MusicFoldersWrapper()

    val musicFolders get() = wrapper.musicFolders
}

internal class MusicFoldersWrapper(
    @JsonProperty("musicFolder") val musicFolders: List<MusicFolder> = emptyList()
)
