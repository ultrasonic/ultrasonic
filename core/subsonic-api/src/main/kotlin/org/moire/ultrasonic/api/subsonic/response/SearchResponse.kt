package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.SearchResult

class SearchResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    val searchResult: SearchResult = SearchResult()
) : SubsonicResponse(status, version, error)
