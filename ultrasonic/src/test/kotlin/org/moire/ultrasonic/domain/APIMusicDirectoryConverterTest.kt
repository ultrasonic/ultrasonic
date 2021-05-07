@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import java.util.Calendar
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Unit test for extension functions in APIMusicDirectoryConverter.kt file.
 */
class APIMusicDirectoryConverterTest {
    @Test
    fun `Should convert MusicDirectory entity`() {
        val entity = MusicDirectory(
            id = "1982", parent = "345", name = "some-name", userRating = 3,
            averageRating = 3.4f, starred = Calendar.getInstance(), playCount = 10,
            childList = listOf(MusicDirectoryChild("1"), MusicDirectoryChild("2"))
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            name `should be equal to` entity.name
            getAllChild().size `should be equal to` entity.childList.size
            getAllChild() `should be equal to` entity.childList
                .map { it.toDomainEntity() }.toMutableList()
        }
    }

    @Test
    fun `Should convert MusicDirectoryChild entity`() {
        val entity = MusicDirectoryChild(
            id = "929", parent = "11", title = "some-title",
            album = "some-album", albumId = "231", artist = "some-artist", artistId = "1233",
            track = 12, year = 2002, genre = "some-genre", coverArt = "952", size = 9418123L,
            contentType = "some-content-type", suffix = "some-suffix",
            transcodedContentType = "some-transcoded-content-type",
            transcodedSuffix = "some-transcoded-suffix", duration = 11, bitRate = 256,
            path = "some-path", isDir = true, isVideo = true, playCount = 323, discNumber = 2,
            created = Calendar.getInstance(), type = "some-type",
            starred = Calendar.getInstance(), userRating = 3, averageRating = 2.99F
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should be equal to` entity.id
            parent `should be equal to` entity.parent
            isDirectory `should be equal to` entity.isDir
            title `should be equal to` entity.title
            album `should be equal to` entity.album
            albumId `should be equal to` entity.albumId
            artist `should be equal to` entity.artist
            artistId `should be equal to` entity.artistId
            track `should be equal to` entity.track
            year `should be equal to` entity.year!!
            genre `should be equal to` entity.genre
            contentType `should be equal to` entity.contentType
            suffix `should be equal to` entity.suffix
            transcodedContentType `should be equal to` entity.transcodedContentType
            transcodedSuffix `should be equal to` entity.transcodedSuffix
            coverArt `should be equal to` entity.coverArt
            size `should be equal to` entity.size
            duration `should be equal to` entity.duration
            bitRate `should be equal to` entity.bitRate
            path `should be equal to` entity.path
            isVideo `should be equal to` entity.isVideo
            created `should be equal to` entity.created?.time
            starred `should be equal to` (entity.starred != null)
            discNumber `should be equal to` entity.discNumber
            type `should be equal to` entity.type
            userRating `should be equal to` entity.userRating
            averageRating `should be equal to` entity.averageRating
        }
    }

    @Test
    fun `Should convert MusicDirectoryChild podcast entity`() {
        val entity = MusicDirectoryChild(
            id = "584", streamId = "394",
            artist = "some-artist", publishDate = Calendar.getInstance()
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should be equal to` entity.streamId
            artist `should be equal to` dateFormat.format(entity.publishDate!!.time)
        }
    }

    @Test
    fun `Should convert list of MusicDirectoryChild to domain entity list`() {
        val entitiesList = listOf(MusicDirectoryChild(id = "45"), MusicDirectoryChild(id = "34"))

        val domainList = entitiesList.toDomainEntityList()

        domainList.size `should be equal to` entitiesList.size
        domainList.forEachIndexed { index, entry ->
            entry `should be equal to` entitiesList[index].toDomainEntity()
        }
    }
}
