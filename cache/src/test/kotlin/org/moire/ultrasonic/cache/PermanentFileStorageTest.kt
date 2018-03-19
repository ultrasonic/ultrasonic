package org.moire.ultrasonic.cache

import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should equal to`
import org.amshove.kluent.`should equal`
import org.junit.Test
import org.moire.ultrasonic.cache.serializers.getMusicFolderSerializer
import org.moire.ultrasonic.domain.MusicFolder
import java.io.File

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

        storageDir.exists() `should equal to` true
        getServerStorageDir().exists() `should equal to` true
    }

    @Test
    fun `Should serialize to file`() {
        val item = MusicFolder("1", "23")
        val name = "some-name"

        storage.store(name, item, getMusicFolderSerializer())

        val storageFiles = getServerStorageDir().listFiles()
        storageFiles.size `should equal to` 1
        storageFiles[0].name `should contain` name
    }

    @Test
    fun `Should deserialize stored object`() {
        val item = MusicFolder("some", "nice")
        val name = "some-name"
        storage.store(name, item, getMusicFolderSerializer())

        val loadedItem = storage.load(name, getMusicFolderSerializer())

        loadedItem `should equal` item
    }

    @Test
    fun `Should overwrite existing stored object`() {
        val name = "some-nice-name"
        val item1 = MusicFolder("1", "1")
        val item2 = MusicFolder("2", "2")
        storage.store(name, item1, getMusicFolderSerializer())
        storage.store(name, item2, getMusicFolderSerializer())

        val loadedItem = storage.load(name, getMusicFolderSerializer())

        loadedItem `should equal` item2
    }

    @Test
    fun `Should clear all files when clearAll is called`() {
        storage.store("name1", MusicFolder("1", "1"), getMusicFolderSerializer())
        storage.store("name2", MusicFolder("2", "2"), getMusicFolderSerializer())

        storage.clearAll()

        getServerStorageDir().listFiles().size `should equal to` 0
    }

    @Test
    fun `Should return null if serialized file not available`() {
        val loadedItem = storage.load("some-name", getMusicFolderSerializer())

        loadedItem `should equal` null
    }

    private fun getServerStorageDir() = File(storageDir, serverId)
}
