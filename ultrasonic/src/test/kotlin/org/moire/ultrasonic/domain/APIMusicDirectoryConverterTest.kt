@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import java.util.Calendar

/**
 * Unit test for extension functions in APIMusicDirectoryConverter.kt file.
 */
class APIMusicDirectoryConverterTest {
    @Test
    fun `Should convert MusicDirectory entity`() {
        val entity = MusicDirectory(id = "1982", parent = "345", name = "some-name", userRating = 3,
                averageRating = 3.4f, starred = Calendar.getInstance(), playCount = 10,
                childList = listOf(MusicDirectoryChild("1"), MusicDirectoryChild("2")))

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            name `should equal to` entity.name
            children.size `should equal to` entity.childList.size
            children `should equal` entity.childList.map { it.toDomainEntity() }.toMutableList()
        }
    }

    @Test
    fun `Should convert MusicDirectoryChild entity`() {
        val entity = MusicDirectoryChild(id = "929", parent = "11", title = "some-title",
                album = "some-album", albumId = "231", artist = "some-artist", artistId = "1233",
                track = 12, year = 2002, genre = "some-genre", coverArt = "952", size = 9418123L,
                contentType = "some-content-type", suffix = "some-suffix",
                transcodedContentType = "some-transcoded-content-type",
                transcodedSuffix = "some-transcoded-suffix", duration = 11, bitRate = 256,
                path = "some-path", isDir = true, isVideo = true, playCount = 323, discNumber = 2,
                created = Calendar.getInstance(), type = "some-type",
                starred = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id
            parent `should equal to` entity.parent
            isDirectory `should equal to` entity.isDir
            title `should equal` entity.title
            album `should equal` entity.album
            albumId `should equal to` entity.albumId
            artist `should equal to` entity.artist
            artistId `should equal to` entity.artistId
            track `should equal to` entity.track
            year `should equal to` entity.year!!
            genre `should equal to` entity.genre
            contentType `should equal to` entity.contentType
            suffix `should equal to` entity.suffix
            transcodedContentType `should equal to` entity.transcodedContentType
            transcodedSuffix `should equal to` entity.transcodedSuffix
            coverArt `should equal to` entity.coverArt
            size `should equal to` entity.size
            duration `should equal to` entity.duration
            bitRate `should equal to` entity.bitRate
            path `should equal to` entity.path
            isVideo `should equal to` entity.isVideo
            created `should equal` entity.created?.time
            starred `should equal to` (entity.starred != null)
            discNumber `should equal to` entity.discNumber
            type `should equal to` entity.type
        }
    }

    @Test
    fun `Should convert MusicDirectoryChild podcast entity`() {
        val entity = MusicDirectoryChild(id = "584", streamId = "394",
                artist = "some-artist", publishDate = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.streamId
            artist `should equal to` dateFormat.format(entity.publishDate?.time)
        }
    }

    @Test
    fun `Should convert list of MusicDirectoryChild to domain entity list`() {
        val entitiesList = listOf(MusicDirectoryChild(id = "45"), MusicDirectoryChild(id = "34"))

        val domainList = entitiesList.toDomainEntityList()

        domainList.size `should equal to` entitiesList.size
        domainList.forEachIndexed { index, entry ->
            entry `should equal` entitiesList[index].toDomainEntity()
        }
    }
}
