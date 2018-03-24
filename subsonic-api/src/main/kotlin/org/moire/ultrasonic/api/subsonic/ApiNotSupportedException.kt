package org.moire.ultrasonic.api.subsonic

import java.io.IOException

/**
 * Special [IOException] to indicate that called api is not yet supported
 * by current server api version.
 */
class ApiNotSupportedException(
    serverApiVersion: SubsonicAPIVersions
) : IOException("Server api $serverApiVersion does not support this call")
