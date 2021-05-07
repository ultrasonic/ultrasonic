package org.moire.ultrasonic.api.subsonic.models

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Unit test for [SubsonicResponse.Status] class
 */
@RunWith(Parameterized::class)
class StatusTest(private val status: SubsonicResponse.Status) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<SubsonicResponse.Status> = SubsonicResponse.Status.values().toList()
    }

    @Test
    fun `Should proper parse response status`() {
        SubsonicResponse.Status.getStatusFromJson(status.jsonValue) `should be equal to` status
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Should throw IllegalArgumentException on unknown status`() {
        SubsonicResponse.Status.getStatusFromJson(status.jsonValue.plus("-some"))
    }
}
