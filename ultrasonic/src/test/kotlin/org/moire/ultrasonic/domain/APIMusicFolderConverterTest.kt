@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicFolder

/**
 * Unit test for extension functions in file APIMusicFolderConverter.kt.
 */
class APIMusicFolderConverterTest {
    @Test
    fun `Should convert MusicFolder entity`() {
        val entity = MusicFolder(id = "10", name = "some-name")

        val convertedEntity = entity.toDomainEntity()

        convertedEntity.name `should equal to` entity.name
        convertedEntity.id `should equal to` entity.id
    }

    @Test
    fun `Should convert list of MusicFolder entities`() {
        val entityList = listOf(
                MusicFolder(id = "3", name = "some-name-3"),
                MusicFolder(id = "4", name = "some-name-4"))

        val convertedList = entityList.toDomainEntityList()

        with(convertedList) {
            size `should equal to` entityList.size
            this[0].id `should equal to` entityList[0].id
            this[0].name `should equal to` entityList[0].name
            this[1].id `should equal to` entityList[1].id
            this[1].name `should equal to` entityList[1].name
        }
    }
}
