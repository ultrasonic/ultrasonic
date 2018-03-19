@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import java.util.Calendar

/**
 * Unit test for extension functions in APIArtistConverter.kt file.
 */
class APIArtistConverterTest {
    @Test
    fun `Should convert artist entity`() {
        val entity = Artist(id = "10", name = "artist-name", starred = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal` entity.id
            name `should equal` entity.name
        }
    }

    @Test
    fun `Should convert Artist entity to domain MusicDirectory entity`() {
        val entity = Artist(id = "101", name = "artist-name", coverArt = "some-art",
                albumCount = 10,
                albumsList = listOf(Album(id = "562", name = "some-name", coverArt = "zzz",
                        artist = "artist-name", artistId = "256", songCount = 10, duration = 345,
                        created = Calendar.getInstance(), year = 2011, genre = "Math Rock")))

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should equal` entity.name
            getAllChild() `should equal` entity.albumsList
                    .map { it.toDomainEntity() }.toMutableList()
        }
    }
}
