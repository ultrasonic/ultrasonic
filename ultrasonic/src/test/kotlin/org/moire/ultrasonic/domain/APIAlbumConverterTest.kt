@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import java.util.Calendar

/**
 * Unit test for extension functions in [APIAlbumConverter.kt] file.
 */
class APIAlbumConverterTest {
    @Test
    fun `Should convert Album to domain entity`() {
        val entity = Album(id = "387", name = "some-name", coverArt = "asdas",
                artist = "some-artist", artistId = "390", songCount = 12, duration = 841,
                created = Calendar.getInstance(), year = 2017, genre = "some-genre")

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal` entity.id
            title `should equal` entity.name
            isDirectory `should equal` true
            coverArt `should equal` entity.coverArt
            artist `should equal` entity.artist
            artistId `should equal` entity.artistId
            songCount `should equal` entity.songCount.toLong()
            duration `should equal` entity.duration
            created `should equal` entity.created?.time
            year `should equal` entity.year
            genre `should equal` entity.genre
        }
    }

    @Test
    fun `Should convert to MusicDirectory domain entity`() {
        val entity = Album(id = "101", name = "some-album", artist = "some-artist", artistId = "54",
                coverArt = "some-id", songCount = 10, duration = 456,
                created = Calendar.getInstance(), year = 2022, genre = "Hard Rock",
                songList = listOf(MusicDirectoryChild()))

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should equal` null
            getChildren().size `should be equal to` entity.songList.size
            getChildren()[0] `should equal` entity.songList[0].toDomainEntity()
        }
    }

    @Test
    fun `Should convert list of Album entities to domain list entities`() {
        val entityList = listOf(Album(id = "455"), Album(id = "1"), Album(id = "1000"))

        val convertedList = entityList.toDomainEntityList()

        with(convertedList) {
            size `should be equal to` entityList.size
            forEachIndexed { index, entry ->
                entry `should equal` entityList[index].toDomainEntity()
            }
        }
    }
}
