package org.moire.ultrasonic.api.subsonic.response

import org.amshove.kluent.`should equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicError.GENERIC

/**
 * Unit test for [StreamResponse].
 */
class StreamResponseTest {
    @Test
    fun `Should have error if subsonic error is not null`() {
        StreamResponse(apiError = GENERIC).hasError() `should equal to` true
    }

    @Test
    fun `Should have error if http error is not null`() {
        StreamResponse(requestErrorCode = 500).hasError() `should equal to` true
    }

    @Test
    fun `Should not have error if subsonic error and http error is null`() {
        StreamResponse().hasError() `should equal to` false
    }
}
