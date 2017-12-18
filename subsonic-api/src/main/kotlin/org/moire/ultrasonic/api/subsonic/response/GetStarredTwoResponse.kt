package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

class GetStarredTwoResponse(status: Status,
                            version: SubsonicAPIVersions,
                            error: SubsonicError?,
                            val starred2: SearchTwoResult = SearchTwoResult())
    : SubsonicResponse(status, version, error)
