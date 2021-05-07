package org.moire.ultrasonic.cache.serializers

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.cache.BaseStorageTest
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Indexes

/**
 * Test [Indexes] domain entity serializer.
 */
class IndexesSerializerTest : BaseStorageTest() {
    @Test
    fun `Should correctly serialize Indexes object`() {
        val item = Indexes(
            220L, "", mutableListOf(Artist("12")),
            mutableListOf(Artist("233", "some"))
        )

        storage.store("some-name", item, getIndexesSerializer())

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize Indexes object`() {
        val name = "some-name"
        val item = Indexes(
            220L, "", mutableListOf(Artist("12")),
            mutableListOf(Artist("233", "some"))
        )
        storage.store(name, item, getIndexesSerializer())

        val loadedItem = storage.load(name, getIndexesSerializer())

        loadedItem `should be equal to` item
    }
}
