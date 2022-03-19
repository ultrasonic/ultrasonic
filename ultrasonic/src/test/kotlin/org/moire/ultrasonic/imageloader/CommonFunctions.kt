package org.moire.ultrasonic.imageloader

import java.io.InputStream
import okio.buffer
import okio.source

fun Any.loadResourceStream(name: String): InputStream {
    val source = javaClass.classLoader!!.getResourceAsStream(name).source().buffer()
    return source.inputStream()
}
