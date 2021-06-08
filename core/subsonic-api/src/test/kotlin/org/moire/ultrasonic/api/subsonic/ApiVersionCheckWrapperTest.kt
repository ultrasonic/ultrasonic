package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should throw`
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_1_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_2_0
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.BY_GENRE

/**
 * Unit test for [ApiVersionCheckWrapper].
 */
class ApiVersionCheckWrapperTest {
    private val apiMock = mock<SubsonicAPIDefinition>()
    private val wrapper = ApiVersionCheckWrapper(apiMock, V1_1_0)

    @Test
    fun `Should just call real api for ping`() {
        wrapper.ping()

        verify(apiMock).ping()
    }

    @Test
    fun `Should throw ApiNotSupportedException when current api level is too low for call`() {
        val throwCall = { wrapper.getBookmarks() }

        throwCall `should throw` ApiNotSupportedException::class
        verify(apiMock, never()).getBookmarks()
    }

    @Test
    fun `Should throw ApiNotSupportedException when call param is not supported by current api`() {
        wrapper.currentApiVersion = V1_2_0

        wrapper.getAlbumList(BY_GENRE)

        val throwCall = { wrapper.getAlbumList(BY_GENRE, musicFolderId = "12") }

        throwCall `should throw` ApiNotSupportedException::class
        verify(apiMock).getAlbumList(BY_GENRE)
        verify(apiMock, never()).getAlbumList(BY_GENRE, musicFolderId = "12")
    }
}
