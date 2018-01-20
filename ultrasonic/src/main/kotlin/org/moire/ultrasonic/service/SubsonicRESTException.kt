package org.moire.ultrasonic.service

import org.moire.ultrasonic.api.subsonic.SubsonicError

/**
 * Exception returned by API with given `code`.
 */
class SubsonicRESTException(val error: SubsonicError) : Exception("Api error: ${error.code}") {
    val code: Int get() = error.code
}
