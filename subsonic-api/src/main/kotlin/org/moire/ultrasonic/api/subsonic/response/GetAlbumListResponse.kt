package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetAlbumListResponse(status: Status,
                           version: SubsonicAPIVersions,
                           error: SubsonicError?)
    : SubsonicResponse(status, version, error) {
    @JsonProperty("albumList") private val albumWrapper = AlbumWrapper()

    val albumList: List<MusicDirectoryChild>
        get() = albumWrapper.albumList
}

private class AlbumWrapper(
        @JsonProperty("album") val albumList: List<MusicDirectoryChild> = emptyList())
