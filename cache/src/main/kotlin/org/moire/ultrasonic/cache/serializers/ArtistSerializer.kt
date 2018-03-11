@file:JvmMultifileClass
@file:JvmName("DomainSerializers")
package org.moire.ultrasonic.cache.serializers

import com.twitter.serial.serializer.CollectionSerializers
import com.twitter.serial.serializer.ObjectSerializer
import com.twitter.serial.serializer.SerializationContext
import com.twitter.serial.stream.SerializerDefs
import com.twitter.serial.stream.SerializerInput
import com.twitter.serial.stream.SerializerOutput
import org.moire.ultrasonic.domain.Artist

private const val SERIALIZER_VERSION = 1

/**
 * Serializer/deserializer for [Artist] domain entity.
 */
val artistSerializer get() = object : ObjectSerializer<Artist>(SERIALIZER_VERSION) {
    override fun serializeObject(
            context: SerializationContext,
            output: SerializerOutput<out SerializerOutput<*>>,
            item: Artist
    ) {
        output.writeString(item.id)
                .writeString(item.name)
                .writeString(item.index)
                .writeString(item.coverArt)
                .apply {
                    val albumCount = item.albumCount
                    if (albumCount != null) writeLong(albumCount) else writeNull()
                }
                .writeInt(item.closeness)
    }

    override fun deserializeObject(
            context: SerializationContext,
            input: SerializerInput,
            versionNumber: Int
    ): Artist? {
        if (versionNumber != SERIALIZER_VERSION) return null

        val id = input.readString()
        val name = input.readString()
        val index = input.readString()
        val coverArt = input.readString()
        val albumCount = if (input.peekType() == SerializerDefs.TYPE_NULL) {
            input.readNull()
            null
        } else {
            input.readLong()
        }
        val closeness = input.readInt()
        return Artist(id, name, index, coverArt, albumCount, closeness)
    }
}

/**
 * Serializer/deserializer for list of [Artist] domain entities.
 */
val artistListSerializer = CollectionSerializers.getListSerializer(artistSerializer)
