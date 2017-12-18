package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Share

class SharesResponse(status: Status,
                     version: SubsonicAPIVersions,
                     error: SubsonicError?)
    : SubsonicResponse(status, version, error) {
    @JsonProperty("shares") private val wrappedShares = SharesWrapper()

    val shares get() = wrappedShares.share
}

internal class SharesWrapper(val share: List<Share> = emptyList())
