package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Indexes

class GetIndexesResponse(status: Status,
                         version: SubsonicAPIVersions,
                         error: SubsonicError?,
                         val indexes: Indexes?) :
        SubsonicResponse(status, version, error)