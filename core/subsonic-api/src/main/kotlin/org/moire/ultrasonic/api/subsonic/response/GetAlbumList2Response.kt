package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Album

@Suppress("NamingConventionViolation")
class GetAlbumList2Response(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("albumList2") private val albumWrapper2 = AlbumWrapper2()

    val albumList: List<Album>
        get() = albumWrapper2.albumList
}

@Suppress("NamingConventionViolation")
private class AlbumWrapper2(
    @JsonProperty("album") val albumList: List<Album> = emptyList()
)
