package org.moire.ultrasonic.subsonic.loader.image

import android.net.Uri
import org.amshove.kluent.shouldEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestCreatorTest {
    @Test
    fun `Should create valid load cover art request`() {
        val entityId = "299"
        val expectedUri = Uri.parse("$SCHEME://$AUTHORITY/$COVER_ART_PATH?$QUERY_ID=$entityId")

        createLoadCoverArtRequest(entityId).compareTo(expectedUri).shouldEqualTo(0)
    }

    @Test
    fun `Should create valid avatar request`() {
        val username = "some-username"
        val expectedUri = Uri.parse("$SCHEME://$AUTHORITY/$AVATAR_PATH?$QUERY_USERNAME=$username")

        createLoadAvatarRequest(username).compareTo(expectedUri).shouldEqualTo(0)
    }
}
