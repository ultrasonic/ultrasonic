package org.moire.ultrasonic.cache

import com.twitter.serial.serializer.SerializationContext
import com.twitter.serial.serializer.Serializer
import com.twitter.serial.stream.Serial
import com.twitter.serial.stream.bytebuffer.ByteBufferSerial
import java.io.File

typealias DomainEntitySerializer<T> = Serializer<T>

internal const val STORAGE_DIR_NAME = "persistent_storage"

/**
 * Provides access to permanent file based storage.
 *
 * [serverId] is currently active server. Should be unique per server so stored data will not
 * interfere with other server data.
 *
 * Look at [org.moire.ultrasonic.cache.serializers] package for available [DomainEntitySerializer]s.
 */
class PermanentFileStorage(
    private val directories: Directories,
    private val serverId: String,
    private val debug: Boolean = false
) {
    private val serializationContext = object : SerializationContext {
        override fun isDebug(): Boolean = debug
        override fun isRelease(): Boolean = !debug
    }

    private val serializer: Serial = ByteBufferSerial(serializationContext)

    /**
     * Stores given [objectToStore] using [name] as a key and [objectSerializer] as serializer.
     */
    fun <T> store(
        name: String,
        objectToStore: T,
        objectSerializer: DomainEntitySerializer<T>
    ) {
        val storeFile = getFile(name)
        if (!storeFile.exists()) storeFile.createNewFile()
        storeFile.writeBytes(serializer.toByteArray(objectToStore, objectSerializer))
    }

    /**
     * Loads object with [name] key using [objectDeserializer] deserializer.
     */
    fun <T> load(
        name: String,
        objectDeserializer: DomainEntitySerializer<T>
    ): T? {
        val storeFile = getFile(name)
        if (!storeFile.exists()) return null

        return serializer.fromByteArray(storeFile.readBytes(), objectDeserializer)
    }

    /**
     * Clear all files in storage.
     */
    fun clearAll() {
        val storageDir = getStorageDir()
        storageDir.listFiles().forEach { it.deleteRecursively() }
    }

    private fun getFile(name: String) = File(getStorageDir(), "$name.ser")

    private fun getStorageDir(): File {
        val mainDir = File(directories.getInternalDataDir(), STORAGE_DIR_NAME)
        val serverDir = File(mainDir, serverId)
        if (!serverDir.exists()) serverDir.mkdirs()
        return serverDir
    }
}
