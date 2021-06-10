package org.moire.ultrasonic.imageloader

import android.net.Uri
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import java.io.IOException
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverArtRequestHandlerTest {
    private val mockApiClient: SubsonicAPIClient = mock()
    private val handler = CoverArtRequestHandler(mockApiClient)

    @Test
    fun `Should accept only cover art request`() {
        val requestUri = createLoadCoverArtRequest("some-id")

        handler.canHandleRequest(requestUri.buildRequest()) shouldBeEqualTo true
    }

    @Test
    fun `Should not accept random request uri`() {
        val requestUri = Uri.Builder()
            .scheme(SCHEME)
            .appendPath("random")
            .build()

        handler.canHandleRequest(requestUri.buildRequest()) shouldBeEqualTo false
    }

    @Test
    fun `Should fail loading if uri doesn't contain id`() {
        var requestUri = createLoadCoverArtRequest("some-id")
        requestUri = requestUri.buildUpon().clearQuery().build()

        val fail = {
            handler.load(requestUri.buildRequest(), 0)
        }

        fail `should throw` IllegalArgumentException::class
    }

    @Test
    fun `Should throw IOException when request to api failed`() {
        val streamResponse = StreamResponse(null, null, 500)

        whenever(mockApiClient.getCoverArt(any(), anyOrNull())).thenReturn(streamResponse)

        val fail = {
            handler.load(createLoadCoverArtRequest("some").buildRequest(), 0)
        }

        fail `should throw` IOException::class
    }

    @Test
    fun `Should load bitmap from network`() {
        val streamResponse = StreamResponse(
            loadResourceStream("Big_Buck_Bunny.jpeg"),
            apiError = null,
            responseHttpCode = 200
        )

        whenever(mockApiClient.getCoverArt(any(), anyOrNull())).thenReturn(streamResponse)

        val response = handler.load(
            createLoadCoverArtRequest("some").buildRequest(), 0
        )

        response.loadedFrom `should be equal to` Picasso.LoadedFrom.NETWORK
        response.source `should not be` null
    }

    private fun Uri.buildRequest() = Request.Builder(this).stableKey("-1").build()
}
