package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetRandomSongsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("randomSongs") private val songsWrapper = RandomSongsWrapper()

    val songsList
        get() = songsWrapper.songsList
}

private class RandomSongsWrapper(
    @JsonProperty("song") val songsList: List<MusicDirectoryChild> = emptyList()
)
