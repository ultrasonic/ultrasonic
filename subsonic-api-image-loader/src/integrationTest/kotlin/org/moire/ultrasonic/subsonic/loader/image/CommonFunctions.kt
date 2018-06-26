package org.moire.ultrasonic.subsonic.loader.image

import okio.Okio
import java.io.InputStream

fun Any.loadResourceStream(name: String): InputStream {
    val source = Okio.buffer(Okio.source(javaClass.classLoader.getResourceAsStream(name)))
    return source.inputStream()
}
