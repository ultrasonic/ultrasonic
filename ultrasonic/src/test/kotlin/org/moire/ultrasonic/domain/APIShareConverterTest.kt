@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.Share

/**
 * Unit test for api to domain share entity converter functions.
 */
class APIShareConverterTest : BaseTest() {
    @Test
    fun `Should convert share entity to domain`() {
        val entity = createFakeShare()

        val domainEntity = entity.toDomainEntity(serverId)

        with(domainEntity) {
            id `should be equal to` entity.id
            url `should be equal to` entity.url
            description `should be equal to` entity.description
            username `should be equal to` entity.username
            created `should be equal to` shareTimeFormat.format(entity.created!!.time)
            lastVisited `should be equal to` shareTimeFormat.format(entity.lastVisited!!.time)
            expires `should be equal to` shareTimeFormat.format(entity.expires!!.time)
            visitCount `should be equal to` entity.visitCount.toLong()
            this.getEntries() `should be equal to` entity.items.toDomainEntityList(serverId)
        }
    }

    private fun createFakeShare(): Share {
        return Share(
            id = "45", url = "some-long-url", username = "Bender",
            created = Calendar.getInstance(), expires = Calendar.getInstance(), visitCount = 24,
            description = "Kiss my shiny metal ass", lastVisited = Calendar.getInstance(),
            items = listOf(MusicDirectoryChild())
        )
    }

    @Test
    fun `Should parse list of shares into domain entity list`() {
        val entityList = listOf(
            createFakeShare(),
            createFakeShare().copy(id = "554", lastVisited = null)
        )

        val domainEntityList = entityList.toDomainEntitiesList(serverId)

        domainEntityList.size `should be equal to` entityList.size
        domainEntityList[0] `should be equal to` entityList[0].toDomainEntity(serverId)
        domainEntityList[1] `should be equal to` entityList[1].toDomainEntity(serverId)
    }
}
