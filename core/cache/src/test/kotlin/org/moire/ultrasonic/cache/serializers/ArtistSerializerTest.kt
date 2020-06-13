package org.moire.ultrasonic.cache.serializers

import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.cache.BaseStorageTest
import org.moire.ultrasonic.domain.Artist

/**
 * [Artist] serializers test.
 */
class ArtistSerializerTest : BaseStorageTest() {
    @Test
    fun `Should correctly serialize Artist object`() {
        val item = Artist("id", "name", "index", "coverArt", 1, 0)

        storage.store("some-name", item, getArtistsSerializer())

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize Artist object`() {
        val itemName = "some-name"
        val item = Artist("id", "name", "index", "coverArt", null, 0)
        storage.store(itemName, item, getArtistsSerializer())

        val loadedItem = storage.load(itemName, getArtistsSerializer())

        loadedItem `should equal` item
    }

    @Test
    fun `Should correctly serialize list of Artists`() {
        val itemsList = listOf(
            Artist(id = "1"),
            Artist(id = "2", name = "some")
        )

        storage.store("some-name", itemsList, getArtistListSerializer())

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize list of Artists`() {
        val name = "some-name"
        val itemsList = listOf(
            Artist(id = "1"),
            Artist(id = "2", name = "some")
        )
        storage.store(name, itemsList, getArtistListSerializer())

        val loadedItems = storage.load(name, getArtistListSerializer())

        loadedItems `should equal` itemsList
    }
}
