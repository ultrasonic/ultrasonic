package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicFolder

class MusicFoldersResponse(status: Status,
                           version: SubsonicAPIVersions,
                           error: SubsonicError?,
                           @JsonDeserialize(using = MusicFoldersDeserializer::class)
                           val musicFolders: List<MusicFolder> = emptyList()) :
        SubsonicResponse(status, version, error) {
    companion object {
        class MusicFoldersDeserializer() : JsonDeserializer<List<MusicFolder>>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): List<MusicFolder> {
                p.nextToken()
                if (p.currentName == "musicFolder" && p.nextToken() == JsonToken.START_ARRAY) {
                    val mfJavaType = ctxt!!.typeFactory
                            .constructCollectionType(List::class.java, MusicFolder::class.java)
                    return ctxt.readValue(p, mfJavaType)
                }

                throw JsonMappingException(p, "Failed to parse music folders list")
            }
        }
    }
}