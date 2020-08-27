package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should equal`
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit test for [SubsonicAPIVersions] class.
 */
@RunWith(Parameterized::class)
class SubsonicAPIVersionsTest(private val apiVersion: SubsonicAPIVersions) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<SubsonicAPIVersions> = SubsonicAPIVersions.values().asList()
    }

    @Test
    fun `Should proper convert api version to enum`() {
        SubsonicAPIVersions.getClosestKnownClientApiVersion(apiVersion.restApiVersion) `should equal` apiVersion
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Should throw IllegalArgumentException for unknown api version`() {
        SubsonicAPIVersions.getClosestKnownClientApiVersion(apiVersion.restApiVersion.substring(0, 2))
    }
}
