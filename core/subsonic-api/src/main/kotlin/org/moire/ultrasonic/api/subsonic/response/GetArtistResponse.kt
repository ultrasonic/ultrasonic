package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Artist

class GetArtistResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    val artist: Artist = Artist()
) : SubsonicResponse(status, version, error)
