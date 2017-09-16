package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

class GetStarredResponse(status: Status,
                         version: SubsonicAPIVersions,
                         error: SubsonicError?,
                         val starred: SearchTwoResult = SearchTwoResult())
    : SubsonicResponse(status, version, error)
