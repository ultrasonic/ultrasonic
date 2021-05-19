package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken.END_OBJECT
import com.fasterxml.jackson.core.JsonToken.START_OBJECT
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * Common API errors.
 */
@Suppress("MagicNumber")
@JsonDeserialize(using = SubsonicError.Companion.SubsonicErrorDeserializer::class)
sealed class SubsonicError(val code: Int) {
    data class Generic(val message: String) : SubsonicError(0)
    object RequiredParamMissing : SubsonicError(10)
    object IncompatibleClientProtocolVersion : SubsonicError(20)
    object IncompatibleServerProtocolVersion : SubsonicError(30)
    object WrongUsernameOrPassword : SubsonicError(40)
    object TokenAuthNotSupportedForLDAP : SubsonicError(41)
    object UserNotAuthorizedForOperation : SubsonicError(50)
    object TrialPeriodIsOver : SubsonicError(60)
    object RequestedDataWasNotFound : SubsonicError(70)

    companion object {
        fun getError(code: Int, message: String) = when (code) {
            0 -> Generic(message)
            10 -> RequiredParamMissing
            20 -> IncompatibleClientProtocolVersion
            30 -> IncompatibleServerProtocolVersion
            40 -> WrongUsernameOrPassword
            41 -> TokenAuthNotSupportedForLDAP
            50 -> UserNotAuthorizedForOperation
            60 -> TrialPeriodIsOver
            70 -> RequestedDataWasNotFound
            else -> throw IllegalArgumentException("Unknown code $code")
        }

        class SubsonicErrorDeserializer : JsonDeserializer<SubsonicError>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): SubsonicError {
                var code = -1
                var message = ""
                while (p.nextToken() != END_OBJECT) {
                    when {
                        p.currentToken == START_OBJECT -> p.skipChildren()
                        "code".equals(p.currentName, ignoreCase = true) ->
                            code = p.nextIntValue(-1)
                        "message".equals(p.currentName, ignoreCase = true) ->
                            message = p.nextTextValue()
                    }
                }
                return getError(code, message)
            }
        }
    }
}
