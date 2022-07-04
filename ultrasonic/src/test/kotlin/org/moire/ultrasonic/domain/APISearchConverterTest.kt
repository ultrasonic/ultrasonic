@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchResult
import org.moire.ultrasonic.api.subsonic.models.SearchThreeResult
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult
import org.moire.ultrasonic.data.ActiveServerProvider

/**
 * Unit test for extension function in APISearchConverter.kt file.
 */
class APISearchConverterTest : BaseTest() {
    @Test
    fun `Should convert SearchResult to domain entity`() {
        val entity = SearchResult(
            offset = 10,
            totalHits = 3,
            matchList = listOf(
                MusicDirectoryChild(id = "101")
            )
        )

        val convertedEntity = entity.toDomainEntity(serverId)

        with(convertedEntity) {
            albums `should not be equal to` null
            albums.size `should be equal to` 0
            artists `should not be equal to` null
            artists.size `should be equal to` 0
            songs.size `should be equal to` entity.matchList.size
            songs[0] `should be equal to` entity.matchList[0].toTrackEntity(serverId)
        }
    }

    @Test
    fun `Should convert SearchTwoResult to domain entity`() {
        val entity = SearchTwoResult(
            listOf(Artist(id = "82", name = "great-artist-name")),
            listOf(Album(id = "762", artist = "bzz")),
            listOf(MusicDirectoryChild(id = "9118", parent = "112"))
        )

        val convertedEntity = entity.toDomainEntity(ActiveServerProvider.getActiveServerId())

        with(convertedEntity) {
            artists.size `should be equal to` entity.artistList.size
            artists[0] `should be equal to` entity.artistList[0].toIndexEntity(serverId)
            albums.size `should be equal to` entity.albumList.size
            albums[0] `should be equal to` entity.albumList[0].toDomainEntity(serverId)
            songs.size `should be equal to` entity.songList.size
            songs[0] `should be equal to` entity.songList[0].toTrackEntity(serverId)
        }
    }

    @Test
    fun `Should convert SearchThreeResult to domain entity`() {
        val entity = SearchThreeResult(
            artistList = listOf(Artist(id = "612", name = "artist1")),
            albumList = listOf(Album(id = "221", name = "album1")),
            songList = listOf(MusicDirectoryChild(id = "7123", title = "song1"))
        )

        val convertedEntity = entity.toDomainEntity(serverId)

        with(convertedEntity) {
            artists.size `should be equal to` entity.artistList.size
            artists[0] `should be equal to` entity.artistList[0].toDomainEntity(serverId)
            albums.size `should be equal to` entity.albumList.size
            albums[0] `should be equal to` entity.albumList[0].toDomainEntity(serverId)
            songs.size `should be equal to` entity.songList.size
            songs[0] `should be equal to` entity.songList[0].toTrackEntity(serverId)
        }
    }
}
