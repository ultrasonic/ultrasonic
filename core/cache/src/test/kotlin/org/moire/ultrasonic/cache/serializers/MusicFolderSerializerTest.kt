package org.moire.ultrasonic.cache.serializers

import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.cache.BaseStorageTest
import org.moire.ultrasonic.domain.MusicFolder

/**
 * [MusicFolder] serializers test.
 */
class MusicFolderSerializerTest : BaseStorageTest() {
    @Test
    fun `Should correctly serialize MusicFolder object`() {
        val item = MusicFolder("Music", "Folder")

        storage.store("some-name", item, getMusicFolderSerializer())

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize MusicFolder object`() {
        val name = "name"
        val item = MusicFolder("some", "none")
        storage.store(name, item, getMusicFolderSerializer())

        val loadedItem = storage.load(name, getMusicFolderSerializer())

        loadedItem `should equal` item
    }

    @Test
    fun `Should correctly serialize list of MusicFolders objects`() {
        val itemsList = listOf(
            MusicFolder("1", "1"),
            MusicFolder("2", "2")
        )

        storage.store("some-name", itemsList, getMusicFolderListSerializer())

        validateSerializedData()
    }

    @Test
    fun `Should correctly deserialize list of MusicFolder objects`() {
        val name = "some-name"
        val itemsList = listOf(
            MusicFolder("1", "1"),
            MusicFolder("2", "2")
        )
        storage.store(name, itemsList, getMusicFolderListSerializer())

        val loadedItem = storage.load(name, getMusicFolderListSerializer())

        loadedItem `should equal` itemsList
    }
}
