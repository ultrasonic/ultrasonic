@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist

/**
 * Unit test for extension functions in APIArtistConverter.kt file.
 */
class APIArtistConverterTest : BaseTest() {
    @Test
    fun `Should convert artist entity`() {
        val entity = Artist(id = "10", name = "artist-name", starred = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity(serverId)

        with(convertedEntity) {
            id `should be equal to` entity.id
            name `should be equal to` entity.name
        }
    }

    @Test
    fun `Should convert Artist entity to domain MusicDirectory entity`() {
        val entity = Artist(
            id = "101", name = "artist-name", coverArt = "some-art",
            albumCount = 10,
            albumsList = listOf(
                Album(
                    id = "562", name = "some-name", coverArt = "zzz",
                    artist = "artist-name", artistId = "256", songCount = 10, duration = 345,
                    created = Calendar.getInstance(), year = 2011, genre = "Math Rock"
                )
            )
        )

        val convertedEntity = entity.toMusicDirectoryDomainEntity(serverId)

        with(convertedEntity) {
            name `should be equal to` entity.name
            getChildren() `should be equal to` entity.albumsList
                .map { it.toDomainEntity(serverId) }.toMutableList()
        }
    }
}
