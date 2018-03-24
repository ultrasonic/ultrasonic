@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.PodcastChannel

/**
 * Unit test for extension functions in APIPodcastConverter.kt file.
 */
class APIPodcastConverterTest {
    @Test
    fun `Should convert podcast channel entity to domain entity`() {
        val entity = PodcastChannel(id = "452", url = "some-url", title = "some-title",
                description = "some-description", coverArt = "cA", originalImageUrl = "image-url",
                status = "podcast-status", errorMessage = "some-error-message")

        val converterEntity = entity.toDomainEntity()

        with(converterEntity) {
            id `should equal` entity.id
            description `should equal` entity.description
            status `should equal` entity.status
            title `should equal` entity.title
            url `should equal` entity.url
        }
    }

    @Test
    fun `Should convert list of podcasts channels to domain entites list`() {
        val entitiesList = listOf(
                PodcastChannel(id = "932", title = "title1"),
                PodcastChannel(id = "12", title = "title2"))

        val converted = entitiesList.toDomainEntitiesList()

        with(converted) {
            size `should be equal to` entitiesList.size
            this[0] `should equal` entitiesList[0].toDomainEntity()
        }
    }
}
