package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongsByGenreResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("songsByGenre") private val songsByGenreList = SongsByGenreWrapper()

    val songsList get() = songsByGenreList.songsList
}

internal class SongsByGenreWrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
