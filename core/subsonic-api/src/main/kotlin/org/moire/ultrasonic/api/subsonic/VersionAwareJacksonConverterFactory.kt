package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import java.lang.reflect.Type
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

/**
 * Retrofit Converter Factory which uses Jackson for conversion and maintains the
 * version of the Subsonic API.
 * @param notifier: callback function to call when the Subsonic API version changes
 */
class VersionAwareJacksonConverterFactory(
    private val notifier: (SubsonicAPIVersions) -> Unit = {}
) : Converter.Factory() {

    constructor(
        notifier: (SubsonicAPIVersions) -> Unit = {},
        mapper: ObjectMapper
    ) : this(notifier) {
        this.mapper = mapper
        jacksonConverterFactory = JacksonConverterFactory.create(mapper)
    }

    private var mapper: ObjectMapper? = null
    private var jacksonConverterFactory: JacksonConverterFactory? = null

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val javaType: JavaType = mapper!!.typeFactory.constructType(type)
        val reader: ObjectReader? = mapper!!.readerFor(javaType)
        return VersionAwareResponseBodyConverter<Any>(notifier, reader!!)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        return jacksonConverterFactory?.requestBodyConverter(
            type, parameterAnnotations, methodAnnotations, retrofit
        )
    }

    companion object {
        @JvmOverloads // Guarding public API nullability.
        fun create(
            notifier: (SubsonicAPIVersions) -> Unit = {},
            mapper: ObjectMapper? = ObjectMapper()
        ): VersionAwareJacksonConverterFactory {
            if (mapper == null) throw NullPointerException("mapper == null")
            return VersionAwareJacksonConverterFactory(notifier, mapper)
        }
    }

    @Suppress("SwallowedException")
    class VersionAwareResponseBodyConverter<T> (
        private val notifier: (SubsonicAPIVersions) -> Unit = {},
        private val adapter: ObjectReader
    ) : Converter<ResponseBody, T> {
        override fun convert(value: ResponseBody): T {
            value.use {
                // The response stream contains the version of the API for parsing the stream
                // to an object. Currently the parsing is independent from the version as new
                // versions only contain extra optional fields.
                val response: T = adapter.readValue(value.charStream())
                if (response is SubsonicResponse) {
                    try {
                        notifier(response.version)
                    } catch (e: IllegalArgumentException) {
                        // no-op
                    }
                }
                return response
            }
        }
    }
}
