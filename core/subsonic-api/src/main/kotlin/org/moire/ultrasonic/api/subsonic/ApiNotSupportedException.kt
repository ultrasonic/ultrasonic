package org.moire.ultrasonic.api.subsonic

import java.io.IOException

/**
 * Special [IOException] to indicate that called api is not yet supported
 * by current server api version.
 */
class ApiNotSupportedException : IOException {
    val serverApiVersion: String
    constructor(
        apiVersion: SubsonicAPIVersions
    ) : super("Server api $apiVersion does not support this call") {
        serverApiVersion = apiVersion.restApiVersion
    }
}
