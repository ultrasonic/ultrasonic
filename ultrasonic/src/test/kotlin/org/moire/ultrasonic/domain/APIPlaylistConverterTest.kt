@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.Playlist

/**
 * Unit test for extension functions that converts api playlist entity to domain.
 */
class APIPlaylistConverterTest {
    @Test
    fun `Should convert Playlist to MusicDirectory domain entity`() {
        val entity = Playlist(
            name = "some-playlist-name",
            entriesList = listOf(
                MusicDirectoryChild(id = "10", parent = "1393"),
                MusicDirectoryChild(id = "11", parent = "1393")
            )
        )

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should be equal to` entity.name
            size `should be equal to` entity.entriesList.size
            this[0] `should be equal to` entity.entriesList[0].toDomainEntity()
            this[1] `should be equal to` entity.entriesList[1].toDomainEntity()
        }
    }

    @Test
    fun `Should convert playlist to domain entity`() {
        val entity = Playlist(
            id = "634", name = "some-name", owner = "some-owner",
            comment = "some-comment", public = false, songCount = 256, duration = 1150,
            created = Calendar.getInstance(), changed = Calendar.getInstance(),
            coverArt = "some-art"
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should be equal to` entity.id
            name `should be equal to` entity.name
            comment `should be equal to` entity.comment
            owner `should be equal to` entity.owner
            public `should be equal to` entity.public
            songCount `should be equal to` entity.songCount.toString()
            created `should be equal to` playlistDateFormat.format(entity.created!!.time)
        }
    }

    @Test
    fun `Should convert list of playlists to list of domain entities`() {
        val entitiesList = listOf(Playlist(id = "23", name = "some-name", songCount = 10))

        val convertedList = entitiesList.toDomainEntitiesList()

        with(convertedList) {
            size `should be equal to` entitiesList.size
            this[0] `should be equal to` entitiesList[0].toDomainEntity()
        }
    }
}
