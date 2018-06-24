package org.moire.ultrasonic.cache

import android.content.Context
import java.io.File

/**
 * Provides specific to Android implementation of [Directories].
 */
class AndroidDirectories(
    private val context: Context
) : Directories {
    override fun getInternalCacheDir(): File = context.cacheDir

    override fun getInternalDataDir(): File = context.filesDir

    override fun getExternalCacheDir(): File? = context.externalCacheDir
}
