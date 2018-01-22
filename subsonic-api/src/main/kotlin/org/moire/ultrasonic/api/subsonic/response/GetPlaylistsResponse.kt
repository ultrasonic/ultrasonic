package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Playlist

class GetPlaylistsResponse(status: Status,
                           version: SubsonicAPIVersions,
                           error: SubsonicError?)
    : SubsonicResponse(status, version, error) {
    @JsonProperty("playlists")
    private val playlistsWrapper: PlaylistsWrapper = PlaylistsWrapper()

    val playlists: List<Playlist>
        get() = playlistsWrapper.playlistList
}

private class PlaylistsWrapper(
        @JsonProperty("playlist") val playlistList: List<Playlist> = emptyList())
