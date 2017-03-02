package org.moire.ultrasonic.api.subsonic

/**
 * Common API errors.
 */
enum class SubsonicError(val code: Int) {
    GENERIC(0),
    REQUIRED_PARAM_MISSING(10),
    INCOMPATIBLE_CLIENT_PROTOCOL_VERSION(20),
    INCOMPATIBLE_SERVER_PROTOCOL_VERSION(30),
    WRONG_USERNAME_OR_PASSWORD(40),
    TOKEN_AUTH_NOT_SUPPORTED_FOR_LDAP(41),
    USER_NOT_AUTHORIZED_FOR_OPERATION(50),
    TRIAL_PERIOD_IS_OVER(60),
    REQUESTED_DATA_WAS_NOT_FOUND(70);

    companion object {
        fun parseErrorFromJson(jsonErrorCode: Int) = SubsonicError.values()
                .filter { it.code == jsonErrorCode }.firstOrNull()
                ?: throw IllegalArgumentException("Unknown code $jsonErrorCode")
    }
}