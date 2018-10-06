package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Genre

class GenresResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("genres") private val genresWrapper = GenresWrapper()
    val genresList: List<Genre> get() = genresWrapper.genresList
}

internal class GenresWrapper(@JsonProperty("genre") val genresList: List<Genre> = emptyList())
