@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.MusicFolder
import java.util.Calendar

/**
 * Unit test for functions in SubsonicAPIConverter file.
 *
 * @author Yahor Berdnikau
 */
@Suppress("TooManyFunctions")
class APIConverterTest {
    @Test
    fun `Should convert MusicFolder entity`() {
        val entity = createMusicFolder(10, "some-name")

        val convertedEntity = entity.toDomainEntity()

        convertedEntity.name `should equal to` entity.name
        convertedEntity.id `should equal to` entity.id.toString()
    }

    @Test
    fun `Should convert list of MusicFolder entities`() {
        val entityList = listOf(
                createMusicFolder(3, "some-name-3"),
                createMusicFolder(4, "some-name-4")
        )

        val convertedList = entityList.toDomainEntityList()

        with(convertedList) {
            size `should equal to` entityList.size
            this[0].id `should equal to` entityList[0].id.toString()
            this[0].name `should equal to` entityList[0].name
            this[1].id `should equal to` entityList[1].id.toString()
            this[1].name `should equal to` entityList[1].name
        }
    }

    @Test
    fun `Should convert artist entity`() {
        val entity = createArtist(10, "artist-name", Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id.toString()
            name `should equal to` entity.name
        }
    }

    @Test
    fun `Should convert Indexes entity`() {
        val artistsA = listOf(createArtist(4, "AC/DC"), createArtist(45, "ABBA"))
        val artistsT = listOf(createArtist(10, "Taproot"), createArtist(12, "Teebee"))
        val entity = createIndexes(154, "Le Tre Ze", listOf(
                createIndex("A", artistsA),
                createIndex("T", artistsT)
        ), artistsA)

        val convertedEntity = entity.toDomainEntity()

        val expectedArtists = (artistsA + artistsT).map { it.toDomainEntity() }.toMutableList()
        with(convertedEntity) {
            lastModified `should equal to` entity.lastModified
            ignoredArticles `should equal to` entity.ignoredArticles
            artists.size `should equal to` expectedArtists.size
            artists `should equal` expectedArtists
            shortcuts `should equal` artistsA.map { it.toDomainEntity() }.toMutableList()
        }
    }

    @Test
    fun `Should convert MusicDirectory entity`() {
        val entity = MusicDirectory(id = 1982L, parent = 345L, name = "some-name", userRating = 3,
                averageRating = 3.4f, starred = Calendar.getInstance(), playCount = 10,
                childList = listOf(MusicDirectoryChild(1L), MusicDirectoryChild(2L)))

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            name `should equal to` entity.name
            children.size `should equal to` entity.childList.size
            children `should equal` entity.childList.map { it.toDomainEntity() }.toMutableList()
        }
    }

    @Test
    fun `Should convert MusicDirectoryChild entity`() {
        val entity = MusicDirectoryChild(id = 929L, parent = 11L, title = "some-title",
                album = "some-album", albumId = 231L, artist = "some-artist", artistId = 1233L,
                track = 12, year = 2002, genre = "some-genre", coverArt = "952", size = 9418123L,
                contentType = "some-content-type", suffix = "some-suffix",
                transcodedContentType = "some-transcoded-content-type",
                transcodedSuffix = "some-transcoded-suffix", duration = 11, bitRate = 256,
                path = "some-path", isDir = true, isVideo = true, playCount = 323, discNumber = 2,
                created = Calendar.getInstance(), type = "some-type", starred = Calendar.getInstance())

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id.toString()
            parent `should equal to` entity.parent.toString()
            isDirectory `should equal to` entity.isDir
            title `should equal` entity.title
            album `should equal` entity.album
            albumId `should equal to` entity.albumId.toString()
            artist `should equal to` entity.artist
            artistId `should equal to` entity.artistId.toString()
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

    @Test
    fun `Should convert Album to domain entity`() {
        val entity = Album(id = 387L, name = "some-name", coverArt = "asdas", artist = "some-artist",
                artistId = 390L, songCount = 12, duration = 841, created = Calendar.getInstance(),
                year = 2017, genre = "some-genre")

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            id `should equal to` entity.id.toString()
            title `should equal to` entity.name
            coverArt `should equal to` entity.coverArt
            artist `should equal to` entity.artist
            artistId `should equal to` entity.artistId.toString()
            songCount `should equal to` entity.songCount.toLong()
            duration `should equal to` entity.duration
            created `should equal` entity.created?.time
            year `should equal to` entity.year
            genre `should equal to` entity.genre
        }
    }

    private fun createMusicFolder(id: Long = 0, name: String = ""): MusicFolder =
            MusicFolder(id, name)

    private fun createArtist(id: Long = -1, name: String = "", starred: Calendar? = null): Artist
            = Artist(id = id, name = name, starred = starred)

    private fun createIndex(name: String = "", artistList: List<Artist> = emptyList()): Index
            = Index(name, artistList)

    private fun createIndexes(
            lastModified: Long = 0,
            ignoredArticles: String,
            indexList: List<Index> = emptyList(),
            shortcuts: List<Artist> = emptyList()): Indexes
            = Indexes(lastModified, ignoredArticles, indexList, shortcuts)
}
