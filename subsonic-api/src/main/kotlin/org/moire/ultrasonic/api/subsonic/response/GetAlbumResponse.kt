package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Album

class GetAlbumResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    val album: Album = Album()
) : SubsonicResponse(status, version, error)
