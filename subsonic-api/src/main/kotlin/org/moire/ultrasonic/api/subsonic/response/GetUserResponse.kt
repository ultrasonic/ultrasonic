package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.User

class GetUserResponse(
        status: Status,
        version: SubsonicAPIVersions,
        error: SubsonicError?,
        val user: User = User()) : SubsonicResponse(status, version, error)
