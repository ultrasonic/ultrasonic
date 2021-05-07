@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.PodcastChannel

/**
 * Unit test for extension functions in APIPodcastConverter.kt file.
 */
class APIPodcastConverterTest {
    @Test
    fun `Should convert podcast channel entity to domain entity`() {
        val entity = PodcastChannel(
            id = "452", url = "some-url", title = "some-title",
            description = "some-description", coverArt = "cA", originalImageUrl = "image-url",
            status = "podcast-status", errorMessage = "some-error-message"
        )

        val converterEntity = entity.toDomainEntity()

        with(converterEntity) {
            id `should be equal to` entity.id
            description `should be equal to` entity.description
            status `should be equal to` entity.status
            title `should be equal to` entity.title
            url `should be equal to` entity.url
        }
    }

    @Test
    fun `Should convert list of podcasts channels to domain entites list`() {
        val entitiesList = listOf(
            PodcastChannel(id = "932", title = "title1"),
            PodcastChannel(id = "12", title = "title2")
        )

        val converted = entitiesList.toDomainEntitiesList()

        with(converted) {
            size `should be equal to` entitiesList.size
            this[0] `should be equal to` entitiesList[0].toDomainEntity()
        }
    }
}
