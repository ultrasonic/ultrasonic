package org.moire.ultrasonic.imageloader

import java.io.InputStream
import okio.Okio

fun Any.loadResourceStream(name: String): InputStream {
    val source = Okio.buffer(Okio.source(javaClass.classLoader!!.getResourceAsStream(name)))
    return source.inputStream()
}
