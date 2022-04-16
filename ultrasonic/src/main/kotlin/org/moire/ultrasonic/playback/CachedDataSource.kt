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
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import org.moire.ultrasonic.util.AbstractFile
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Storage
import timber.log.Timber

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CachedDataSource(
    private var upstreamDataSource: DataSource
) : BaseDataSource(false) {

    class Factory(
        private var upstreamDataSourceFactory: DataSource.Factory
    ) : DataSource.Factory {

        override fun createDataSource(): CachedDataSource {
            return createDataSourceInternal(
                upstreamDataSourceFactory.createDataSource()
            )
        }

        private fun createDataSourceInternal(
            upstreamDataSource: DataSource
        ): CachedDataSource {
            return CachedDataSource(
                upstreamDataSource
            )
        }
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
            "CachedDatasource: Open: %s",
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
            transferStarted(dataSpec)
            skipFully(dataSpec.position, dataSpec)
            return bytesToRead
        }

        // else forward the call to upstream
        return upstreamDataSource.open(dataSpec)
    }

    @Suppress("MagicNumber")
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset > 0 || length > 4)
            Timber.d("CachedDatasource: Read: %s %s", offset, length)
        return if (cachePath != null) {
            try {
                readInternal(buffer, offset, length)
            } catch (e: IOException) {
                throw HttpDataSourceException.createForIOException(
                    e, Util.castNonNull(dataSpec), HttpDataSourceException.TYPE_READ
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
            Timber.i("CachedDatasource: EndOfInput")
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read.toLong()
        bytesTransferred(read)
        return read
    }

    /**
     * Attempts to skip the specified number of bytes in full.
     *
     * @param bytesToSkip The number of bytes to skip.
     * @param dataSpec The [DataSpec].
     * @throws HttpDataSourceException If the thread is interrupted during the operation, or an error
     * occurs while reading from the source, or if the data ended before skipping the specified
     * number of bytes.
     */
    @Suppress("ThrowsCount")
    @Throws(HttpDataSourceException::class)
    private fun skipFully(bytesToSkip: Long, dataSpec: DataSpec) {
        var bytesToSkip = bytesToSkip
        if (bytesToSkip == 0L) {
            return
        }
        val skipBuffer = ByteArray(4096)
        try {
            while (bytesToSkip > 0) {
                val readLength =
                    bytesToSkip.coerceAtMost(skipBuffer.size.toLong()).toInt()
                val read = Util.castNonNull(responseByteStream).read(skipBuffer, 0, readLength)
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedIOException()
                }
                if (read == -1) {
                    throw HttpDataSourceException(
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        HttpDataSourceException.TYPE_OPEN
                    )
                }
                bytesToSkip -= read.toLong()
                bytesTransferred(read)
            }
            return
        } catch (e: IOException) {
            if (e is HttpDataSourceException) {
                throw e
            } else {
                throw HttpDataSourceException(
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSourceException.TYPE_OPEN
                )
            }
        }
    }

    /*
     * This method is called by StatsDataSource to verify that the loading succeeded,
     * so its important that we return the correct value here..
     */
    override fun getUri(): Uri? {
        return cachePath?.toUri() ?: upstreamDataSource.uri
    }

    override fun close() {
        Timber.i("CachedDatasource: close %s", openedFile)
        if (openedFile) {
            openedFile = false
            transferEnded()
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

        val descriptor = cacheFile!!.getDocumentFileDescriptor("r")
        val length = descriptor!!.length
        descriptor.close()

        return length
    }
}
