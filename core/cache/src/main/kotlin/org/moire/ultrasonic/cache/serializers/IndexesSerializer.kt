@file:JvmMultifileClass
@file:JvmName("DomainSerializers")
package org.moire.ultrasonic.cache.serializers

import com.twitter.serial.serializer.ObjectSerializer
import com.twitter.serial.serializer.SerializationContext
import com.twitter.serial.stream.SerializerInput
import com.twitter.serial.stream.SerializerOutput
import org.moire.ultrasonic.cache.DomainEntitySerializer
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Indexes

private const val SERIALIZATION_VERSION = 1

private val indexesSerializer get() = object : ObjectSerializer<Indexes>(SERIALIZATION_VERSION) {
    override fun serializeObject(
        context: SerializationContext,
        output: SerializerOutput<out SerializerOutput<*>>,
        item: Indexes
    ) {
        val artistListSerializer = getArtistListSerializer()
        output.writeLong(item.lastModified)
            .writeString(item.ignoredArticles)
            .writeObject<MutableList<Artist>>(context, item.shortcuts, artistListSerializer)
            .writeObject<MutableList<Artist>>(context, item.artists, artistListSerializer)
    }

    @Suppress("ReturnCount")
    override fun deserializeObject(
        context: SerializationContext,
        input: SerializerInput,
        versionNumber: Int
    ): Indexes? {
        if (versionNumber != SERIALIZATION_VERSION) return null

        val artistListDeserializer = getArtistListSerializer()
        val lastModified = input.readLong()
        val ignoredArticles = input.readString() ?: return null
        val shortcutsList = input.readObject(context, artistListDeserializer) ?: return null
        val artistsList = input.readObject(context, artistListDeserializer) ?: return null
        return Indexes(
            lastModified, ignoredArticles, shortcutsList.toMutableList(),
            artistsList.toMutableList()
        )
    }
}

/**
 * Get serializer/deserializer for [Indexes] entity.
 */
fun getIndexesSerializer(): DomainEntitySerializer<Indexes> = indexesSerializer
