/*
 * CachedDataSource.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.CacheIgnoredReason
import java.io.IOException
import java.io.InputStream
import org.moire.ultrasonic.util.AbstractFile
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Storage
import timber.log.Timber

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CachedDataSource(
    private var upstreamDataSource: DataSource,
    private var eventListener: EventListener?
) : BaseDataSource(false) {

    class Factory(
        private var upstreamDataSourceFactory: DataSource.Factory
    ) : DataSource.Factory {

        private var eventListener: EventListener? = null

        /**
         * Sets the {link EventListener} to which events are delivered.
         *
         *
         * The default is `null`.
         *
         * @param eventListener The [EventListener].
         * @return This factory.
         */
        fun setEventListener(eventListener: EventListener?): Factory {
            this.eventListener = eventListener
            return this
        }

        override fun createDataSource(): CachedDataSource {
            return createDataSourceInternal(
                upstreamDataSourceFactory.createDataSource()
            )
        }

        private fun createDataSourceInternal(
            upstreamDataSource: DataSource
        ): CachedDataSource {
            return CachedDataSource(
                upstreamDataSource,
                eventListener
            )
        }
    }

    /** Listener of [CacheDataSource] events.  */
    interface EventListener {
        /**
         * Called when bytes have been read from the cache.
         *
         * @param cacheSizeBytes Current cache size in bytes.
         * @param cachedBytesRead Total bytes read from the cache since this method was last called.
         */
        fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long)

        /**
         * Called when the current request ignores cache.
         *
         * @param reason Reason cache is bypassed.
         */
        fun onCacheIgnored(reason: @CacheIgnoredReason Int)
    }

    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var dataSpec: DataSpec? = null
    private var responseByteStream: InputStream? = null
    private var openedFile = false
    private var cachePath: String? = null
    private var cacheFile: AbstractFile? = null

    override fun open(dataSpec: DataSpec): Long {
        Timber.i(
            "CachedDatasource: Open: %s %s %s",
            dataSpec.uri,
            dataSpec.position,
            dataSpec.toString()
        )

        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = 0

        val components = dataSpec.uri.toString().split('|')
        val path = components[2]
        val cacheLength = checkCache(path)

        // We have found an item in the cache, return early
        if (cacheLength > 0) {
            transferInitializing(dataSpec)
            bytesToRead = cacheLength
            return bytesToRead
        }

        // else forward the call to upstream
        return upstreamDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        Timber.i("CachedDatasource: Read: %s %s %s", buffer, offset, length)
        return if (cachePath != null) {
            try {
                readInternal(buffer, offset, length)
            } catch (e: IOException) {
                throw HttpDataSource.HttpDataSourceException.createForIOException(
                    e, Util.castNonNull(dataSpec), HttpDataSource.HttpDataSourceException.TYPE_READ
                )
            }
        } else {
            upstreamDataSource.read(buffer, offset, length)
        }
    }

    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
        var readLength = readLength
        if (readLength == 0) {
            return 0
        }
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLength = readLength.toLong().coerceAtMost(bytesRemaining).toInt()
        }
        val read = Util.castNonNull(responseByteStream).read(buffer, offset, readLength)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read.toLong()
        // TODO
        // bytesTransferred(read)
        return read
    }

    /*
     * This method is called by StatsDataSource to verify that the loading succeeded,
     * so its important that we return the correct value here..
     */
    override fun getUri(): Uri? {
        return cachePath?.toUri() ?: upstreamDataSource.uri
    }

    override fun close() {
        Timber.i("CachedDatasource: close")
        if (openedFile) {
            openedFile = false
            responseByteStream?.close()
            responseByteStream = null
        }
    }

    /**
     * Checks our cache for a matching media file
     */
    private fun checkCache(path: String): Long {
        var filePath: String = path
        var found = Storage.isPathExists(path)

        if (!found) {
            filePath = FileUtil.getCompleteFile(path)
            found = Storage.isPathExists(filePath)
        }

        if (!found) return -1

        cachePath = filePath
        openedFile = true

        cacheFile = Storage.getFromPath(filePath)!!
        responseByteStream = cacheFile!!.getFileInputStream()

        return cacheFile!!.getDocumentFileDescriptor("r")!!.length
    }
}
