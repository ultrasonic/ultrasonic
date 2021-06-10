package org.moire.ultrasonic.api.subsonic

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should throw`
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit test for [SubsonicAPIVersions.SubsonicAPIVersionsDeserializer] class.
 */
class SubsonicAPIVersionsDeserializerTest {
    private val jsonParser = mock<JsonParser>()
    private val context = mock<DeserializationContext>()

    private lateinit var deserializer: SubsonicAPIVersions.Companion.SubsonicAPIVersionsDeserializer

    @Before
    fun setUp() {
        deserializer = SubsonicAPIVersions.Companion.SubsonicAPIVersionsDeserializer()
    }

    @Test
    fun `Should throw if current token name is not version`() {
        doReturn("asdasd").whenever(jsonParser).currentName

        { deserializer.deserialize(jsonParser, context) } `should throw` JsonParseException::class
    }

    @Test
    fun `Should return parsed version`() {
        doReturn("version").whenever(jsonParser).currentName
        doReturn(SubsonicAPIVersions.V1_13_0.restApiVersion).whenever(jsonParser).text

        val parsedVersion = deserializer.deserialize(jsonParser, context)

        parsedVersion `should be` SubsonicAPIVersions.V1_13_0
    }
}
