@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.ChatMessage

/**
 * Unit test for functions converting api [ChatMessage] to domain entity.
 */
class APIChatMessageConverterTest {
    @Test
    fun `Should convert entity`() {
        val entity = ChatMessage("Woohoo", 553434L, "Wow")

        val domainEntity = entity.toDomainEntity()

        with(domainEntity) {
            username `should equal to` entity.username
            time `should equal to` entity.time
            message `should equal to` entity.message
        }
    }

    @Test
    fun `Should convert list of entities`() {
        val entitiesList = listOf(ChatMessage("AAA"), ChatMessage("BBB"))

        val domainEntitiesList = entitiesList.toDomainEntitiesList()

        with(domainEntitiesList) {
            size `should equal to` entitiesList.size
            forEachIndexed { index, chatMessage ->
                chatMessage `should equal` entitiesList[index].toDomainEntity()
            }
        }
    }
}
