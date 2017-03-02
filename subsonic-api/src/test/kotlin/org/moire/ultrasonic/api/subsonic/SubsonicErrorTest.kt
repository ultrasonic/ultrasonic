package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal`
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit test for [SubsonicError].
 */
@RunWith(Parameterized::class)
class SubsonicErrorTest(private val error: SubsonicError) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<SubsonicError> = SubsonicError.values().toList()
    }

    @Test
    fun `Should proper convert error code to error`() {
        SubsonicError.parseErrorFromJson(error.code) `should equal` error
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Should throw IllegalArgumentException from unknown error code`() {
        SubsonicError.parseErrorFromJson(error.code + 10000)
    }
}