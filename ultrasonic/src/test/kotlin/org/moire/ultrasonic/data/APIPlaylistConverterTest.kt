@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.Playlist
import java.util.Calendar

/**
 * Unit test for extension functions that converts api playlist entity to domain.
 */
class APIPlaylistConverterTest {
    @Test
    fun `Should convert Playlist to MusicDirectory domain entity`() {
        val entity = Playlist(name = "some-playlist-name", entriesList = listOf(
                MusicDirectoryChild(id = "10", parent = "1393"),
                MusicDirectoryChild(id = "11", parent = "1393")
        ))

        val convertedEntity = entity.toMusicDirectoryDomainEntity()

        with(convertedEntity) {
            name `should equal to` entity.name
            children.size `should equal to` entity.entriesList.size
            children[0] `should equal` entity.entriesList[0].toDomainEntity()
            children[1] `should equal` entity.entriesList[1].toDomainEntity()
        }
    }

    @Test
    fun `Should convert playlist to domain entity`() {
        val entity = Playlist(id = "634", name = "some-name", owner = "some-owner",
                comment = "some-comment", public = false, songCount = 256, duration = 1150,
                created = Calendar.getInstance(), changed = Calendar.getInstance(),
                coverArt = "some-art")

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id
            name `should equal to` entity.name
            comment `should equal to` entity.comment
            owner `should equal to` entity.owner
            public `should equal to` entity.public
            songCount `should equal to` entity.songCount.toString()
            created `should equal to` playlistDateFormat.format(entity.created?.time)
        }
    }

    @Test
    fun `Should convert list of playlists to list of domain entities`() {
        val entitiesList = listOf(Playlist(id = "23", name = "some-name", songCount = 10))

        val convertedList = entitiesList.toDomainEntitiesList()

        with(convertedList) {
            size `should equal to` entitiesList.size
            this[0] `should equal` entitiesList[0].toDomainEntity()
        }
    }
}
