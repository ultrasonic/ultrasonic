@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Genre

/**
 * Unit test for for converter from api [Genre] to domain entity.
 */
class ApiGenreConverterTest {
    @Test
    fun `Should convert to domain entity`() {
        val entity = Genre(songCount = 220, albumCount = 123, name = "some-name")

        val domainEntity = entity.toDomainEntity()

        with(domainEntity) {
            name `should equal to` entity.name
            index `should equal to` "s"
        }
    }

    @Test
    fun `Should convert a list entites to domain entities`() {
        val entitiesList = listOf(
                Genre(41, 2, "some-name"),
                Genre(12, 3, "other-name"))

        val domainEntitiesList = entitiesList.toDomainEntityList()

        domainEntitiesList.size `should equal to` entitiesList.size
        domainEntitiesList[0] `should equal` entitiesList[0].toDomainEntity()
        domainEntitiesList[1] `should equal` entitiesList[1].toDomainEntity()
    }
}
