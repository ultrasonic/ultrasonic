package org.moire.ultrasonic.subsonic.loader.image

import android.net.Uri
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.shouldEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class CoverArtRequestHandlerTest {
    private val mockSubsonicApiClientMock = mock<SubsonicAPIClient>()
    private val handler = CoverArtRequestHandler(mockSubsonicApiClientMock)

    @Test
    fun `Should accept only cover art request`() {
        val requestUri = createLoadCoverArtRequest("some-id")

        handler.canHandleRequest(requestUri.buildRequest()) shouldEqualTo true
    }

    @Test
    fun `Should not accept random request uri`() {
        val requestUri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath("random")
            .build()

        handler.canHandleRequest(requestUri.buildRequest()) shouldEqualTo false
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
        whenever(mockSubsonicApiClientMock.getCoverArt(any(), anyOrNull()))
            .thenReturn(streamResponse)

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
        whenever(mockSubsonicApiClientMock.getCoverArt(any(), anyOrNull()))
            .thenReturn(streamResponse)

        val response = handler.load(createLoadCoverArtRequest("some").buildRequest(), 0)

        response.loadedFrom `should equal` Picasso.LoadedFrom.NETWORK
        response.source `should not be` null
    }

    private fun Uri.buildRequest() = Request.Builder(this).build()
}
