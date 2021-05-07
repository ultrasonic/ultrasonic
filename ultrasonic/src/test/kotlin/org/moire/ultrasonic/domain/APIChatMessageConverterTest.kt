@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
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
            username `should be equal to` entity.username
            time `should be equal to` entity.time
            message `should be equal to` entity.message
        }
    }

    @Test
    fun `Should convert list of entities`() {
        val entitiesList = listOf(ChatMessage("AAA"), ChatMessage("BBB"))

        val domainEntitiesList = entitiesList.toDomainEntitiesList()

        with(domainEntitiesList) {
            size `should be equal to` entitiesList.size
            forEachIndexed { index, chatMessage ->
                chatMessage `should be equal to` entitiesList[index].toDomainEntity()
            }
        }
    }
}
