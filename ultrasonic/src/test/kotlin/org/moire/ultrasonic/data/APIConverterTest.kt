@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes
import org.moire.ultrasonic.api.subsonic.models.MusicFolder
import java.util.Calendar

/**
 * Unit test for functions in SubsonicAPIConverter file.
 *
 * @author Yahor Berdnikau
 */
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
        ), emptyList())

        val convertedEntity = entity.toDomainEntity()

        val expectedArtists = (artistsA + artistsT).map { it.toDomainEntity() }.toMutableList()
        with(convertedEntity) {
            lastModified `should equal to` entity.lastModified
            ignoredArticles `should equal to` entity.ignoredArticles
            artists.size `should equal to` expectedArtists.size
            artists `should equal` expectedArtists
            shortcuts `should equal` emptyList()
        }
    }

    private fun createMusicFolder(id: Long = 0, name: String = ""): MusicFolder =
            MusicFolder(id, name)

    private fun createArtist(id: Long = -1, name: String = "", starred: Calendar? = null): Artist
            = Artist(id, name, starred)

    private fun createIndex(name: String = "", artistList: List<Artist> = emptyList()): Index
            = Index(name, artistList)

    private fun createIndexes(
            lastModified: Long = 0,
            ignoredArticles: String,
            indexList: List<Index> = emptyList(),
            shortcuts: List<Index> = emptyList()): Indexes
            = Indexes(lastModified, ignoredArticles, indexList, shortcuts)
}
