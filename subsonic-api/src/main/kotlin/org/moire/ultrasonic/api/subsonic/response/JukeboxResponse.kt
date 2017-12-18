package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonSetter
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.JukeboxStatus

class JukeboxResponse(status: Status,
                      version: SubsonicAPIVersions,
                      error: SubsonicError?,
                      var jukebox: JukeboxStatus = JukeboxStatus())
    : SubsonicResponse(status, version, error) {
    @JsonSetter("jukeboxStatus") fun setJukeboxStatus(jukebox: JukeboxStatus) {
        this.jukebox = jukebox
    }

    @JsonSetter("jukeboxPlaylist") fun setJukeboxPlaylist(jukebox: JukeboxStatus) {
        this.jukebox = jukebox
    }
}
