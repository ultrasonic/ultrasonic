@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchResult
import org.moire.ultrasonic.api.subsonic.models.SearchThreeResult
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

/**
 * Unit test for extension function in APISearchConverter.kt file.
 */
class APISearchConverterTest {
    @Test
    fun `Should convert SearchResult to domain entity`() {
        val entity = SearchResult(offset = 10, totalHits = 3, matchList = listOf(
                MusicDirectoryChild(id = "101")
        ))

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            albums `should not equal` null
            albums.size `should equal to` 0
            artists `should not equal` null
            artists.size `should equal to` 0
            songs.size `should equal to` entity.matchList.size
            songs[0] `should equal` entity.matchList[0].toDomainEntity()
        }
    }

    @Test
    fun `Should convert SearchTwoResult to domain entity`() {
        val entity = SearchTwoResult(listOf(
                Artist(id = "82", name = "great-artist-name")
        ), listOf(
                MusicDirectoryChild(id = "762", artist = "bzz")
        ), listOf(
                MusicDirectoryChild(id = "9118", parent = "112")
        ))

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            artists.size `should equal to` entity.artistList.size
            artists[0] `should equal` entity.artistList[0].toDomainEntity()
            albums.size `should equal to` entity.albumList.size
            albums[0] `should equal` entity.albumList[0].toDomainEntity()
            songs.size `should equal to` entity.songList.size
            songs[0] `should equal` entity.songList[0].toDomainEntity()
        }
    }

    @Test
    fun `Should convert SearchThreeResult to domain entity`() {
        val entity = SearchThreeResult(
                artistList = listOf(Artist(id = "612", name = "artist1")),
                albumList = listOf(Album(id = "221", name = "album1")),
                songList = listOf(MusicDirectoryChild(id = "7123", title = "song1"))
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            artists.size `should equal to` entity.artistList.size
            artists[0] `should equal` entity.artistList[0].toDomainEntity()
            albums.size `should equal to` entity.albumList.size
            albums[0] `should equal` entity.albumList[0].toDomainEntity()
            songs.size `should equal to` entity.songList.size
            songs[0] `should equal` entity.songList[0].toDomainEntity()
        }
    }
}
