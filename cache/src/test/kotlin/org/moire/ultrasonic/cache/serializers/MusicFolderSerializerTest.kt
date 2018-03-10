package org.moire.ultrasonic.cache.serializers

import com.twitter.serial.util.SerializationUtils
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

        storage.store("some-name", item, musicFolderSerializer)

        val serializedFileBytes = storageDir.listFiles()[0].readBytes()
        SerializationUtils.validateSerializedData(serializedFileBytes)
    }

    @Test
    fun `Should correctly deserialize MusicFolder object`() {
        val name = "name"
        val item = MusicFolder("some", "none")
        storage.store(name, item, musicFolderSerializer)

        val loadedItem = storage.load(name, musicFolderSerializer)

        loadedItem `should equal` item
    }

    @Test
    fun `Should correctly serialize list of MusicFolders objects`() {
        val itemsList = listOf(
                MusicFolder("1", "1"),
                MusicFolder("2", "2")
        )

        storage.store("some-name", itemsList, musicFolderListSerializer)

        val serializedFileBytes = storageDir.listFiles()[0].readBytes()
        SerializationUtils.validateSerializedData(serializedFileBytes)
    }

    @Test
    fun `Should correctly deserialize list of MusicFolder objects`() {
        val name = "some-name"
        val itemsList = listOf(
                MusicFolder("1", "1"),
                MusicFolder("2", "2")
        )
        storage.store(name, itemsList, musicFolderListSerializer)

        val loadedItem = storage.load(name, musicFolderListSerializer)

        loadedItem `should equal` itemsList
    }
}
