@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import java.util.Calendar

/**
 * Unit test for extension functions in [APIArtistConverter.kt] file.
 */
class APIArtistConverterTest {
    @Test
    fun `Should convert artist entity`() {
        val entity = Artist(id = 10, name = "artist-name", starred = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id.toString()
            name `should equal to` entity.name
        }
    }

    @Test
    fun `Should convert Artist entity to domain MusicDirectory entity`() {
        val entity = Artist(id = 101L, name = "artist-name", coverArt = "some-art", albumCount = 10,
                albumsList = listOf(Album(id = 562L, name = "some-name", coverArt = "zzz",
                        artist = "artist-name", artistId = 256L, songCount = 10, duration = 345,
                        created = Calendar.getInstance(), year = 2011, genre = "Math Rock")))

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should equal to` entity.name
            children `should equal` entity.albumsList.map { it.toDomainEntity() }.toMutableList()
        }
    }
}
