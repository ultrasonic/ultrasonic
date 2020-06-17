package org.moire.ultrasonic.subsonic.loader.image

import java.io.InputStream
import okio.Okio

fun Any.loadResourceStream(name: String): InputStream {
    val source = Okio.buffer(Okio.source(javaClass.classLoader.getResourceAsStream(name)))
    return source.inputStream()
}
