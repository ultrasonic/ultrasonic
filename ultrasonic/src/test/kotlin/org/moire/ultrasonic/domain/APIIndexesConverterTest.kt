@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes

/**
 * Unit tests for extension functions in [APIIndexesConverter.kt].
 */
class APIIndexesConverterTest {
    @Test
    fun `Should convert Indexes entity`() {
        val artistsA = listOf(
            Artist(id = "4", name = "AC/DC"),
            Artist(id = "45", name = "ABBA")
        )
        val artistsT = listOf(
            Artist(id = "10", name = "Taproot"),
            Artist(id = "12", name = "Teebee")
        )
        val entity = Indexes(
            lastModified = 154, ignoredArticles = "Le Tre Ze",
            indexList = listOf(
                Index(name = "A", artists = artistsA),
                Index(name = "T", artists = artistsT)
            ),
            shortcutList = artistsA
        )

        val convertedEntity = entity.toDomainEntity()

        val expectedArtists = (artistsA + artistsT).map { it.toDomainEntity() }.toMutableList()
        with(convertedEntity) {
            lastModified `should be equal to` entity.lastModified
            ignoredArticles `should be equal to` entity.ignoredArticles
            artists.size `should be equal to` expectedArtists.size
            artists `should be equal to` expectedArtists
            shortcuts `should be equal to` artistsA.map { it.toDomainEntity() }.toMutableList()
        }
    }
}
