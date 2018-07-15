package org.moire.ultrasonic.subsonic.loader.image

import android.net.Uri
import com.nhaarman.mockito_kotlin.any
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AvatarRequestHandlerTest {
    private val mockSubsonicApiClient = mock<SubsonicAPIClient>()
    private val handler = AvatarRequestHandler(mockSubsonicApiClient)

    @Test
    fun `Should accept only cover art request`() {
        val requestUri = createLoadAvatarRequest("some-username")

        handler.canHandleRequest(requestUri.buildRequest()) shouldEqualTo true
    }

    @Test
    fun `Should not accept random request uri`() {
        val requestUri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath("something")
            .build()

        handler.canHandleRequest(requestUri.buildRequest()) shouldEqualTo false
    }

    @Test
    fun `Should fail loading if uri doesn't contain username`() {
        var requestUri = createLoadAvatarRequest("some-username")
        requestUri = requestUri.buildUpon().clearQuery().build()

        val fail = {
            handler.load(requestUri.buildRequest(), 0)
        }

        fail `should throw` IllegalStateException::class
    }

    @Test
    fun `Should load avatar from network`() {
        val streamResponse = StreamResponse(
            loadResourceStream("Big_Buck_Bunny.jpeg"),
            apiError = null,
            responseHttpCode = 200
        )
        whenever(mockSubsonicApiClient.getAvatar(any()))
            .thenReturn(streamResponse)

        val response = handler.load(createLoadAvatarRequest("some-username").buildRequest(), 0)

        response.loadedFrom `should equal` Picasso.LoadedFrom.NETWORK
        response.source `should not be` null
    }

    private fun Uri.buildRequest() = Request.Builder(this).build()
}
