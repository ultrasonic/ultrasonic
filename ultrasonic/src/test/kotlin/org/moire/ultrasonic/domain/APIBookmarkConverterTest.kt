@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Bookmark
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Unit test for function that converts [Bookmark] api entity to domain.
 */
class APIBookmarkConverterTest : BaseTest() {

    @Test
    fun `Should convert to domain entity`() {
        val entity = Bookmark(
            412313L, "Awesemo", "Nice", Calendar.getInstance(),
            Calendar.getInstance(), MusicDirectoryChild(id = "12333")
        )

        val domainEntity = entity.toDomainEntity(serverId)

        with(domainEntity) {
            position `should be equal to` entity.position.toInt()
            username `should be equal to` entity.username
            comment `should be equal to` entity.comment
            created `should be equal to` entity.created?.time
            changed `should be equal to` entity.changed?.time
            track `should be equal to` entity.entry.toTrackEntity(serverId)
        }
    }

    @Test
    fun `Should convert list of entities to domain entities`() {
        val entitiesList = listOf(Bookmark(443L), Bookmark(444L))

        val domainEntitiesList = entitiesList.toDomainEntitiesList(serverId)

        domainEntitiesList.size `should be equal to` entitiesList.size
        domainEntitiesList.forEachIndexed { index, bookmark ->
            bookmark `should be equal to` entitiesList[index].toDomainEntity(serverId)
        }
    }
}
