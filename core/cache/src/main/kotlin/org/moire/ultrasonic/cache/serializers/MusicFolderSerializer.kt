@file:JvmMultifileClass
@file:JvmName("DomainSerializers")
package org.moire.ultrasonic.cache.serializers

import com.twitter.serial.serializer.CollectionSerializers
import com.twitter.serial.serializer.ObjectSerializer
import com.twitter.serial.serializer.SerializationContext
import com.twitter.serial.stream.SerializerInput
import com.twitter.serial.stream.SerializerOutput
import org.moire.ultrasonic.cache.DomainEntitySerializer
import org.moire.ultrasonic.domain.MusicFolder

private const val SERIALIZATION_VERSION = 1

private val musicFolderSerializer = object : ObjectSerializer<MusicFolder>(SERIALIZATION_VERSION) {

    override fun serializeObject(
        context: SerializationContext,
        output: SerializerOutput<out SerializerOutput<*>>,
        item: MusicFolder
    ) {
        output.writeString(item.id).writeString(item.name)
    }

    override fun deserializeObject(
        context: SerializationContext,
        input: SerializerInput,
        versionNumber: Int
    ): MusicFolder? {
        if (versionNumber != SERIALIZATION_VERSION) return null

        val id = input.readString() ?: return null
        val name = input.readString() ?: return null
        return MusicFolder(id, name)
    }
}

/**
 * Serializer/deserializer for [MusicFolder] domain entity.
 */
fun getMusicFolderSerializer(): DomainEntitySerializer<MusicFolder> = musicFolderSerializer

private val musicFolderListSerializer =
    CollectionSerializers.getListSerializer(musicFolderSerializer)

/**
 * Serializer/deserializer for [List] of [MusicFolder] items.
 */
fun getMusicFolderListSerializer(): DomainEntitySerializer<List<MusicFolder>> =
    musicFolderListSerializer
