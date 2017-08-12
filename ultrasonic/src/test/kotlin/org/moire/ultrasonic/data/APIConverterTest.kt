@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicFolder

/**
 * Unit test for functions in SubsonicAPIConverter file.
 *
 * @author Yahor Berdnikau
 */
class APIConverterTest {
    @Test
    fun `Should convert MusicFolder entity`() {
        val entity = createMusicFolder(10, "some-name")

        val convertedEntity = entity.toDomainEntity()

        convertedEntity.name `should equal to` "some-name"
        convertedEntity.id `should equal to` 10.toString()
    }

    @Test
    fun `Should convert list of MusicFolder entities`() {
        val entityList = listOf(
                createMusicFolder(3, "some-name-3"),
                createMusicFolder(4, "some-name-4")
        )

        val convertedList = entityList.toDomainEntityList()

        convertedList.size `should equal to` 2
        convertedList[0].id `should equal to` 3.toString()
        convertedList[0].name `should equal to` "some-name-3"
        convertedList[1].id `should equal to` 4.toString()
        convertedList[1].name `should equal to` "some-name-4"
    }

    private fun createMusicFolder(id: Long = 0, name: String = ""): MusicFolder =
            MusicFolder(id, name)
}
