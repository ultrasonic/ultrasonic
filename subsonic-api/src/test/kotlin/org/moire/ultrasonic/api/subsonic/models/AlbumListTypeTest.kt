package org.moire.ultrasonic.api.subsonic.models

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should throw`
import org.junit.Test

/**
 * Unit test for [AlbumListType] class.
 */
class AlbumListTypeTest {
    @Test
    fun `Should create type from string ignoring case`() {
        val type = AlbumListType.SORTED_BY_NAME.typeName.toLowerCase()

        val albumListType = AlbumListType.fromName(type)

        albumListType `should equal` AlbumListType.SORTED_BY_NAME
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
            AlbumListType.fromName(it.typeName) `should equal` it
        }
    }

    @Test
    fun `Should return type name for toString call`() {
        AlbumListType.STARRED.typeName `should equal to` AlbumListType.STARRED.toString()
    }
}
