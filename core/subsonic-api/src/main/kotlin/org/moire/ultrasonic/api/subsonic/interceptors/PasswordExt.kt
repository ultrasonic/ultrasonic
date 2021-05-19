// Contains common extension functions for password interceptors
package org.moire.ultrasonic.api.subsonic.interceptors

private val hexCharsArray = "0123456789ABCDEF".toCharArray()

/**
 * Converts string to hex representation.
 */
fun String.toHexBytes(): String = this.toByteArray().toHexBytes()

/**
 * Converts given [ByteArray] to corresponding hex chars representation.
 */
@Suppress("MagicNumber")
fun ByteArray.toHexBytes(): String {
    val hexChars = CharArray(this.size * 2)
    for (j in 0..this.lastIndex) {
        val v = this[j].toInt().and(0xFF)
        hexChars[j * 2] = hexCharsArray[v.ushr(4)]
        hexChars[j * 2 + 1] = hexCharsArray[v.and(0x0F)]
    }
    return String(hexChars)
}
