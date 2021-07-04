package org.moire.ultrasonic.api.subsonic.models

import java.util.Locale
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should throw`
import org.junit.Test

/**
 * Unit test for [AlbumListType] class.
 */
class AlbumListTypeTest {
    @Test
    fun `Should create type from string ignoring case`() {
        val type = AlbumListType.SORTED_BY_NAME.typeName.lowercase(Locale.ROOT)

        val albumListType = AlbumListType.fromName(type)

        albumListType `should be equal to` AlbumListType.SORTED_BY_NAME
    }

    @Test
    fun `Should throw IllegalArgumentException for unknown type`() {
        val failCall = {
            AlbumListType.fromName("some-not-existing-type")
        }

        failCall `should throw` IllegalArgumentException::class
    }

    @Test
    fun `Should convert type string to corresponding AlbumListType`() {
        AlbumListType.values().forEach {
            AlbumListType.fromName(it.typeName) `should be equal to` it
        }
    }

    @Test
    fun `Should return type name for toString call`() {
        AlbumListType.STARRED.typeName `should be equal to` AlbumListType.STARRED.toString()
    }
}
