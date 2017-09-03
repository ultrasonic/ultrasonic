package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Lyrics

class GetLyricsResponse(status: Status,
                        version: SubsonicAPIVersions,
                        error: SubsonicError?,
                        val lyrics: Lyrics = Lyrics())
    : SubsonicResponse(status, version, error)
