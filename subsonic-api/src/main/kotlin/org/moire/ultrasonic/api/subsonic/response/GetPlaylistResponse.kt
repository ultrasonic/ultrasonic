package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Playlist

class GetPlaylistResponse(
        status: Status,
        version: SubsonicAPIVersions,
        error: SubsonicError?,
        val playlist: Playlist = Playlist()) : SubsonicResponse(status, version, error)
