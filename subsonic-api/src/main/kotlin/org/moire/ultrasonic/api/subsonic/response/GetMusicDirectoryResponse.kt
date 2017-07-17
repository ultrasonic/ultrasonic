package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory

class GetMusicDirectoryResponse(status: Status,
                                version: SubsonicAPIVersions,
                                error: SubsonicError?,
                                @JsonProperty("directory")
                                val musicDirectory: MusicDirectory?) :
        SubsonicResponse(status, version, error)