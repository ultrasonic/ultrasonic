@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.Share
import java.util.Calendar

/**
 * Unit test for api to domain share entity converter functions.
 */
class APIShareConverterTest {
    @Test
    fun `Should convert share entity to domain`() {
        val entity = createFakeShare()

        val domainEntity = entity.toDomainEntity()

        with(domainEntity) {
            id `should equal to` entity.id
            url `should equal to` entity.url
            description `should equal to` entity.description
            username `should equal to` entity.username
            created `should equal to` shareTimeFormat.format(entity.created?.time)
            lastVisited `should equal to` shareTimeFormat.format(entity.lastVisited?.time)
            expires `should equal to` shareTimeFormat.format(entity.expires?.time)
            visitCount `should equal to` entity.visitCount.toLong()
            entries `should equal` entity.items.toDomainEntityList()
        }
    }

    private fun createFakeShare(): Share {
        return Share(id = "45", url = "some-long-url", username = "Bender",
                created = Calendar.getInstance(), expires = Calendar.getInstance(), visitCount = 24,
                description = "Kiss my shiny metal ass", lastVisited = Calendar.getInstance(),
                items = listOf(MusicDirectoryChild()))
    }

    @Test
    fun `Should parse list of shares into domain entity list`() {
        val entityList = listOf(
                createFakeShare(),
                createFakeShare().copy(id = "554", lastVisited = null))

        val domainEntityList = entityList.toDomainEntitiesList()

        domainEntityList.size `should equal to` entityList.size
        domainEntityList[0] `should equal` entityList[0].toDomainEntity()
        domainEntityList[1] `should equal` entityList[1].toDomainEntity()
    }
}
