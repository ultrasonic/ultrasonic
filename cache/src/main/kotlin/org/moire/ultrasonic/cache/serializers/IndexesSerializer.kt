@file:JvmMultifileClass
@file:JvmName("DomainSerializers")
package org.moire.ultrasonic.cache.serializers

import com.twitter.serial.serializer.CollectionSerializers
import com.twitter.serial.serializer.ObjectSerializer
import com.twitter.serial.serializer.SerializationContext
import com.twitter.serial.stream.SerializerInput
import com.twitter.serial.stream.SerializerOutput
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Indexes

private const val SERIALIZATION_VERSION = 1

val indexesSerializer get() = object : ObjectSerializer<Indexes>(SERIALIZATION_VERSION) {
    override fun serializeObject(
            context: SerializationContext,
            output: SerializerOutput<out SerializerOutput<*>>,
            item: Indexes
    ) {
        output.writeLong(item.lastModified)
                .writeString(item.ignoredArticles)
                .writeObject<MutableList<Artist>>(context, item.shortcuts,
                        CollectionSerializers.getListSerializer(artistSerializer))
                .writeObject<MutableList<Artist>>(context, item.artists,
                        CollectionSerializers.getListSerializer(artistSerializer))
    }

    override fun deserializeObject(
            context: SerializationContext,
            input: SerializerInput,
            versionNumber: Int
    ): Indexes? {
        if (versionNumber != SERIALIZATION_VERSION) return null

        val lastModified = input.readLong()
        val ignoredArticles = input.readString() ?: return null
        val shortcutsList = input.readObject(context,
                CollectionSerializers.getListSerializer(artistSerializer)) ?: return null
        val artistsList = input.readObject(context,
                CollectionSerializers.getListSerializer(artistSerializer)) ?: return null
        return Indexes(lastModified, ignoredArticles, shortcutsList, artistsList)
    }
}
