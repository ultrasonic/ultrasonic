package org.moire.ultrasonic.cache.serializers

import org.amshove.kluent.`should equal`
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
        val item = Indexes(220L, "", mutableListOf(
                Artist("12")
        ), mutableListOf(
                Artist("233", "some")
        ))

        storage.store("some-name", item, indexesSerializer)

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize Indexes object`() {
        val name = "some-name"
        val item = Indexes(220L, "", mutableListOf(
                Artist("12")
        ), mutableListOf(
                Artist("233", "some")
        ))
        storage.store(name, item, indexesSerializer)

        val loadedItem = storage.load(name, indexesSerializer)

        loadedItem `should equal` item
    }
}
