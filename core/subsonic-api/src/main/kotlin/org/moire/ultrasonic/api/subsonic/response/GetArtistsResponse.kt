package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Indexes

class GetArtistsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    @JsonProperty("artists") val indexes: Indexes = Indexes()
) : SubsonicResponse(status, version, error)
