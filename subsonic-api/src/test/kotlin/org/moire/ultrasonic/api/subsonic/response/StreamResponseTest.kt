package org.moire.ultrasonic.api.subsonic.response

import org.amshove.kluent.`should equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.SubsonicError.RequestedDataWasNotFound

/**
 * Unit test for [StreamResponse].
 */
class StreamResponseTest {
    @Test
    fun `Should have error if subsonic error is not null`() {
        StreamResponse(apiError = RequestedDataWasNotFound, responseHttpCode = 200)
                .hasError() `should equal to` true
    }

    @Test
    fun `Should have error if http error is greater then 300`() {
        StreamResponse(responseHttpCode = 301).hasError() `should equal to` true
    }

    @Test
    fun `Should have error of http error code is lower then 200`() {
        StreamResponse(responseHttpCode = 199).hasError() `should equal to` true
    }

    @Test
    fun `Should not have error if http code is 200`() {
        StreamResponse(responseHttpCode = 200).hasError() `should equal to` false
    }

    @Test
    fun `Should not have error if http code is 300`() {
        StreamResponse(responseHttpCode = 300).hasError() `should equal to` false
    }
}
