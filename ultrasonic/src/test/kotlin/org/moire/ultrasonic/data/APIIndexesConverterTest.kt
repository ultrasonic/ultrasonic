@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
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
                Artist(id ="4", name = "AC/DC"),
                Artist(id ="45", name = "ABBA"))
        val artistsT = listOf(
                Artist(id = "10", name = "Taproot"),
                Artist(id = "12", name = "Teebee"))
        val entity = Indexes(lastModified = 154, ignoredArticles = "Le Tre Ze", indexList = listOf(
                Index(name = "A", artists = artistsA),
                Index(name = "T", artists = artistsT)
        ), shortcutList = artistsA)

        val convertedEntity = entity.toDomainEntity()

        val expectedArtists = (artistsA + artistsT).map { it.toDomainEntity() }.toMutableList()
        with(convertedEntity) {
            lastModified `should equal to` entity.lastModified
            ignoredArticles `should equal to` entity.ignoredArticles
            artists.size `should equal to` expectedArtists.size
            artists `should equal` expectedArtists
            shortcuts `should equal` artistsA.map { it.toDomainEntity() }.toMutableList()
        }
    }
}
