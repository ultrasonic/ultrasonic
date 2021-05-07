package org.moire.ultrasonic.cache

import java.io.File
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain`
import org.junit.Test
import org.moire.ultrasonic.cache.serializers.getMusicFolderSerializer
import org.moire.ultrasonic.domain.MusicFolder

/**
 * Integration test for [PermanentFileStorage].
 */
class PermanentFileStorageTest : BaseStorageTest() {
    override val serverId: String
        get() = "some-server-id"

    @Test
    fun `Should create storage dir if it is not exist`() {
        val item = MusicFolder("1", "2")
        storage.store("test", item, getMusicFolderSerializer())

        storageDir.exists() `should be equal to` true
        getServerStorageDir().exists() `should be equal to` true
    }

    @Test
    fun `Should serialize to file`() {
        val item = MusicFolder("1", "23")
        val name = "some-name"

        storage.store(name, item, getMusicFolderSerializer())

        val storageFiles = getServerStorageDir().listFiles()
        storageFiles.size `should be equal to` 1
        storageFiles[0].name `should contain` name
    }

    @Test
    fun `Should deserialize stored object`() {
        val item = MusicFolder("some", "nice")
        val name = "some-name"
        storage.store(name, item, getMusicFolderSerializer())

        val loadedItem = storage.load(name, getMusicFolderSerializer())

        loadedItem `should be equal to` item
    }

    @Test
    fun `Should overwrite existing stored object`() {
        val name = "some-nice-name"
        val item1 = MusicFolder("1", "1")
        val item2 = MusicFolder("2", "2")
        storage.store(name, item1, getMusicFolderSerializer())
        storage.store(name, item2, getMusicFolderSerializer())

        val loadedItem = storage.load(name, getMusicFolderSerializer())

        loadedItem `should be equal to` item2
    }

    @Test
    fun `Should clear all files when clearAll is called`() {
        storage.store("name1", MusicFolder("1", "1"), getMusicFolderSerializer())
        storage.store("name2", MusicFolder("2", "2"), getMusicFolderSerializer())

        storage.clearAll()

        getServerStorageDir().listFiles().size `should be equal to` 0
    }

    @Test
    fun `Should return null if serialized file not available`() {
        val loadedItem = storage.load("some-name", getMusicFolderSerializer())

        loadedItem `should be equal to` null
    }

    private fun getServerStorageDir() = File(storageDir, serverId)
}
