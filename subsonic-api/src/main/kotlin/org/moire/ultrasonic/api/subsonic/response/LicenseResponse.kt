package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.License
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

class LicenseResponse(val license: License?,
                      status: Status,
                      version: SubsonicAPIVersions,
                      error: SubsonicError?):
        SubsonicResponse(status, version, error)