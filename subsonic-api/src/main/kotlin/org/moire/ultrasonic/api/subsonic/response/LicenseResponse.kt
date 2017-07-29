package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.License

class LicenseResponse(val license: License = License(),
                      status: Status,
                      version: SubsonicAPIVersions,
                      error: SubsonicError?) :
        SubsonicResponse(status, version, error)