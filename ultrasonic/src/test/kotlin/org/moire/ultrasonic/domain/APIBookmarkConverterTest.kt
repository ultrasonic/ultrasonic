@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Bookmark
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import java.util.Calendar

/**
 * Unit test for function that converts [Bookmark] api entity to domain.
 */
class APIBookmarkConverterTest {
    @Test
    fun `Should convert to domain entity`() {
        val entity = Bookmark(412313L, "Awesemo", "Nice", Calendar.getInstance(),
                Calendar.getInstance(), MusicDirectoryChild(id = "12333"))

        val domainEntity = entity.toDomainEntity()

        with(domainEntity) {
            position `should equal to` entity.position.toInt()
            username `should equal` entity.username
            comment `should equal` entity.comment
            created `should equal` entity.created?.time
            changed `should equal` entity.changed?.time
            entry `should equal` entity.entry.toDomainEntity()
        }
    }

    @Test
    fun `Should convert list of entities to domain entities`() {
        val entitiesList = listOf(Bookmark(443L), Bookmark(444L))

        val domainEntitiesList = entitiesList.toDomainEntitiesList()

        domainEntitiesList.size `should equal to` entitiesList.size
        domainEntitiesList.forEachIndexed({ index, bookmark ->
            bookmark `should equal` entitiesList[index].toDomainEntity()
        })
    }
}
