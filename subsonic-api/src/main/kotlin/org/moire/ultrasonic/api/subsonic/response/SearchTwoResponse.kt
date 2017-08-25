package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

class SearchTwoResponse(status: Status,
                        version: SubsonicAPIVersions,
                        error: SubsonicError?,
                        @JsonProperty("searchResult2") val searchResult: SearchTwoResult = SearchTwoResult())
    : SubsonicResponse(status, version, error)
