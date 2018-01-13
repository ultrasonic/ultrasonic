package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * Common API errors.
 */
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
                p.nextToken() // { -> "code"
                p.nextToken() // "code" -> codeValue
                val code = p.valueAsInt
                p.nextToken() // codeValue -> "message"
                p.nextToken() // "message" -> messageValue
                val message = p.text
                p.nextToken() // value -> }
                return getError(code, message)
            }
        }
    }
}
