/*
 * UltrasonicCache.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import java.io.File
import java.util.NavigableSet

@UnstableApi

class UltrasonicCache : Cache {
    override fun getUid(): Long {
        TODO("Not yet implemented")
    }

    override fun release() {
        // R/O Cache Implementation
    }

    override fun addListener(key: String, listener: Cache.Listener): NavigableSet<CacheSpan> {
        // Not (yet?) implemented
        return emptySet<CacheSpan>() as NavigableSet<CacheSpan>
    }

    override fun removeListener(key: String, listener: Cache.Listener) {
        // Not (yet?) implemented
    }

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> {
        TODO("Not yet implemented")
    }

    override fun getKeys(): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun getCacheSpace(): Long {
        TODO("Not yet implemented")
    }

    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan {
        TODO("Not yet implemented")
    }

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? {
        TODO("Not yet implemented")
    }

    override fun startFile(key: String, position: Long, length: Long): File {
        // R/O Cache Implementation
        return File("NONE")
    }

    override fun commitFile(file: File, length: Long) {
        // R/O Cache Implementation
    }

    override fun releaseHoleSpan(holeSpan: CacheSpan) {
        TODO("Not yet implemented")
    }

    override fun removeResource(key: String) {
        // R/O Cache Implementation
    }

    override fun removeSpan(span: CacheSpan) {
        // R/O Cache Implementation
    }

    override fun isCached(key: String, position: Long, length: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCachedLength(key: String, position: Long, length: Long): Long {
        TODO("Not yet implemented")
    }

    override fun getCachedBytes(key: String, position: Long, length: Long): Long {
        TODO("Not yet implemented")
    }

    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) {
        TODO("Not yet implemented")
    }

    override fun getContentMetadata(key: String): ContentMetadata {
        TODO("Not yet implemented")
    }

}
