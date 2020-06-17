@file:JvmName("RestErrorMapper")
package org.moire.ultrasonic.subsonic

import android.content.Context
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicError.Generic
import org.moire.ultrasonic.api.subsonic.SubsonicError.IncompatibleClientProtocolVersion
import org.moire.ultrasonic.api.subsonic.SubsonicError.IncompatibleServerProtocolVersion
import org.moire.ultrasonic.api.subsonic.SubsonicError.RequestedDataWasNotFound
import org.moire.ultrasonic.api.subsonic.SubsonicError.RequiredParamMissing
import org.moire.ultrasonic.api.subsonic.SubsonicError.TokenAuthNotSupportedForLDAP
import org.moire.ultrasonic.api.subsonic.SubsonicError.TrialPeriodIsOver
import org.moire.ultrasonic.api.subsonic.SubsonicError.UserNotAuthorizedForOperation
import org.moire.ultrasonic.api.subsonic.SubsonicError.WrongUsernameOrPassword
import org.moire.ultrasonic.service.SubsonicRESTException

/**
 * Extension for [SubsonicRESTException] that returns localized error string, that can used to
 * display error reason for user.
 */
fun SubsonicRESTException.getLocalizedErrorMessage(context: Context): String =
    when (error) {
        is Generic -> {
            val message = error.message
            val errorMessage = if (message == "") {
                context.getString(R.string.api_subsonic_generic_no_message)
            } else {
                message
            }
            context.getString(R.string.api_subsonic_generic, errorMessage)
        }
        RequiredParamMissing -> context.getString(R.string.api_subsonic_param_missing)
        IncompatibleClientProtocolVersion ->
            context.getString(R.string.api_subsonic_upgrade_client)
        IncompatibleServerProtocolVersion ->
            context.getString(R.string.api_subsonic_upgrade_server)
        WrongUsernameOrPassword -> context.getString(R.string.api_subsonic_not_authenticated)
        TokenAuthNotSupportedForLDAP ->
            context.getString(R.string.api_subsonic_token_auth_not_supported_for_ldap)
        UserNotAuthorizedForOperation ->
            context.getString(R.string.api_subsonic_not_authorized)
        TrialPeriodIsOver -> context.getString(R.string.api_subsonic_trial_period_is_over)
        RequestedDataWasNotFound ->
            context.getString(R.string.api_subsonic_requested_data_was_not_found)
    }
