@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Unit test for extension functions in [APIAlbumConverter.kt] file.
 */
class APIAlbumConverterTest {
    @Test
    fun `Should convert Album to domain entity`() {
        val entity = Album(
            id = "387", name = "some-name", coverArt = "asdas",
            artist = "some-artist", artistId = "390", songCount = 12, duration = 841,
            created = Calendar.getInstance(), year = 2017, genre = "some-genre"
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should be equal to` entity.id
            title `should be equal to` entity.name
            isDirectory `should be equal to` true
            coverArt `should be equal to` entity.coverArt
            artist `should be equal to` entity.artist
            artistId `should be equal to` entity.artistId
            songCount `should be equal to` entity.songCount.toLong()
            duration `should be equal to` entity.duration
            created `should be equal to` entity.created?.time
            year `should be equal to` entity.year
            genre `should be equal to` entity.genre
        }
    }

    @Test
    fun `Should convert to MusicDirectory domain entity`() {
        val entity = Album(
            id = "101", name = "some-album", artist = "some-artist", artistId = "54",
            coverArt = "some-id", songCount = 10, duration = 456,
            created = Calendar.getInstance(), year = 2022, genre = "Hard Rock",
            songList = listOf(MusicDirectoryChild())
        )

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should be equal to` null
            size `should be equal to` entity.songList.size
            this[0] `should be equal to` entity.songList[0].toTrackEntity()
        }
    }

    @Test
    fun `Should convert list of Album entities to domain list entities`() {
        val entityList = listOf(Album(id = "455"), Album(id = "1"), Album(id = "1000"))

        val convertedList = entityList.toDomainEntityList()

        with(convertedList) {
            size `should be equal to` entityList.size
            forEachIndexed { index, entry ->
                entry `should be equal to` entityList[index].toDomainEntity()
            }
        }
    }
}
