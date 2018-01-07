@file:JvmName("RestErrorMapper")
package org.moire.ultrasonic.subsonic

import android.content.Context
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicError.GENERIC
import org.moire.ultrasonic.api.subsonic.SubsonicError.INCOMPATIBLE_CLIENT_PROTOCOL_VERSION
import org.moire.ultrasonic.api.subsonic.SubsonicError.INCOMPATIBLE_SERVER_PROTOCOL_VERSION
import org.moire.ultrasonic.api.subsonic.SubsonicError.REQUESTED_DATA_WAS_NOT_FOUND
import org.moire.ultrasonic.api.subsonic.SubsonicError.REQUIRED_PARAM_MISSING
import org.moire.ultrasonic.api.subsonic.SubsonicError.TOKEN_AUTH_NOT_SUPPORTED_FOR_LDAP
import org.moire.ultrasonic.api.subsonic.SubsonicError.TRIAL_PERIOD_IS_OVER
import org.moire.ultrasonic.api.subsonic.SubsonicError.USER_NOT_AUTHORIZED_FOR_OPERATION
import org.moire.ultrasonic.api.subsonic.SubsonicError.WRONG_USERNAME_OR_PASSWORD
import org.moire.ultrasonic.service.parser.SubsonicRESTException

/**
 * Extension for [SubsonicRESTException] that returns localized error string, that can used to
 * display error reason for user.
 */
fun SubsonicRESTException.getLocalizedErrorMessage(context: Context): String =
        when (error) {
            GENERIC -> context.getString(R.string.api_subsonic_generic)
            REQUIRED_PARAM_MISSING -> context.getString(R.string.api_subsonic_param_missing)
            INCOMPATIBLE_CLIENT_PROTOCOL_VERSION -> context
                    .getString(R.string.api_subsonic_upgrade_client)
            INCOMPATIBLE_SERVER_PROTOCOL_VERSION -> context
                    .getString(R.string.api_subsonic_upgrade_server)
            WRONG_USERNAME_OR_PASSWORD -> context.getString(R.string.api_subsonic_not_authenticated)
            TOKEN_AUTH_NOT_SUPPORTED_FOR_LDAP -> context
                    .getString(R.string.api_subsonic_token_auth_not_supported_for_ldap)
            USER_NOT_AUTHORIZED_FOR_OPERATION -> context
                    .getString(R.string.api_subsonic_not_authorized)
            TRIAL_PERIOD_IS_OVER -> context.getString(R.string.api_subsonic_trial_period_is_over)
            REQUESTED_DATA_WAS_NOT_FOUND -> context
                    .getString(R.string.api_subsonic_requested_data_was_not_found)
            else -> context.getString(R.string.api_subsonic_unknown_api_error)
        }
