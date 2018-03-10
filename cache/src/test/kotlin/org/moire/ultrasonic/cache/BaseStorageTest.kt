package org.moire.ultrasonic.cache

import com.nhaarman.mockito_kotlin.mock
import org.amshove.kluent.`it returns`
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

internal const val INTERNAL_DATA_FOLDER = "data"
internal const val INTERNAL_CACHE_FOLDER = "cache"
internal const val EXTERNAL_CACHE_FOLDER = "external_cache"

/**
 * Base test class that inits the storage
 */
abstract class BaseStorageTest {
    @get:Rule val tempFileRule = TemporaryFolder()

    protected lateinit var mockDirectories: Directories
    protected lateinit var storage: PermanentFileStorage

    @Before
    fun setUp() {
        mockDirectories = mock<Directories> {
            on { getInternalDataDir() } `it returns` tempFileRule.newFolder(INTERNAL_DATA_FOLDER)
            on { getInternalCacheDir() } `it returns` tempFileRule.newFolder(INTERNAL_CACHE_FOLDER)
            on { getExternalCacheDir() } `it returns` tempFileRule.newFolder(EXTERNAL_CACHE_FOLDER)
        }
        storage = PermanentFileStorage(mockDirectories, true)
    }

    protected val storageDir get() = File(mockDirectories.getInternalDataDir(), STORAGE_DIR_NAME)
}
