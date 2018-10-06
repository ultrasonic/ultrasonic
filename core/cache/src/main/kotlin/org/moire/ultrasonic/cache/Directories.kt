package org.moire.ultrasonic.cache

import java.io.File

/**
 * Provides access to generic directories:
 * - for temporary caches
 * - for permanent data storage
 */
interface Directories {
    fun getInternalCacheDir(): File
    fun getInternalDataDir(): File
    fun getExternalCacheDir(): File?
}
