/*
 * APIDataSource.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.HttpDataSource.RequestProperties
import androidx.media3.datasource.HttpUtil
import androidx.media3.datasource.TransferListener
import com.google.common.net.HttpHeaders
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.moire.ultrasonic.util.AbstractFile
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Storage
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * An [HttpDataSource] that delegates to Square's [Call.Factory].
 *
 *
 * Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the `dataSpec`, [.setRequestProperty] and the default parameters used to
 * construct the instance.
 */
@UnstableApi
open class OkHttpDataSource private constructor(
    subsonicAPIClient: SubsonicAPIClient,
    userAgent: String?,
    cacheControl: CacheControl?,
    defaultRequestProperties: RequestProperties?
) : BaseDataSource(true),
    HttpDataSource {
    companion object {
        init {
            MediaLibraryInfo.registerModule("media3.datasource.okhttp")
        }
    }

    /** [DataSource.Factory] for [OkHttpDataSource] instances.  */
    class Factory(private val subsonicAPIClient: SubsonicAPIClient) : HttpDataSource.Factory {
        private val defaultRequestProperties: RequestProperties = RequestProperties()
        private var userAgent: String? = null
        private var transferListener: TransferListener? = null
        private var cacheControl: CacheControl? = null

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
            return this
        }


        /**
         * Sets the [TransferListener] that will be used.
         *
         *
         * The default is `null`.
         *
         *
         * See [DataSource.addTransferListener].
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        fun setTransferListener(transferListener: TransferListener?): Factory {
            this.transferListener = transferListener
            return this
        }

        override fun createDataSource(): OkHttpDataSource {
            val dataSource = OkHttpDataSource(
                subsonicAPIClient,
                userAgent,
                cacheControl,
                defaultRequestProperties
            )
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }

    }


    private val subsonicAPIClient: SubsonicAPIClient = Assertions.checkNotNull(subsonicAPIClient)
    private val requestProperties: RequestProperties
    private val userAgent: String?
    private val cacheControl: CacheControl?
    private val defaultRequestProperties: RequestProperties?
    private var dataSpec: DataSpec? = null
    private var response: retrofit2.Response<ResponseBody>? = null
    private var responseByteStream: InputStream? = null
    private var openedNetwork = false
    private var openedFile = false
    private var cachePath: String? = null
    private var cacheFile: AbstractFile? = null
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0

    override fun getUri(): Uri? {
        return when {
            cachePath != null -> cachePath!!.toUri()
            response == null -> null
            else -> response!!.raw().request.url.toString().toUri()
        }
    }

    override fun getResponseCode(): Int {
        return if (response == null) -1 else response!!.code()
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return if (response == null) emptyMap() else response!!.headers().toMultimap()
    }

    override fun setRequestProperty(name: String, value: String) {
        Assertions.checkNotNull(name)
        Assertions.checkNotNull(value)
        requestProperties[name] = value
    }

    override fun clearRequestProperty(name: String) {
        Assertions.checkNotNull(name)
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    @Throws(HttpDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = 0

        transferInitializing(dataSpec)
        val components = dataSpec.uri.toString().split('|')
        val id = components[0]
        val bitrate = components[1].toInt()
        val path = components[2]

        val cacheLength = checkCache(path)

        // We have found an item in the cache, return early
        if (cacheLength > 0) {
            bytesToRead = cacheLength
            return bytesToRead
        }

        Timber.i("DATASOURCE: %s", "Start")
        val request = subsonicAPIClient.api.stream(id, bitrate, offset = 0)
        val response: retrofit2.Response<ResponseBody>?
        val streamResponse: StreamResponse
        Timber.i("DATASOURCE: %s", "Start2")
        try {
            this.response = request.execute()
            Timber.i("DATASOURCE: %s", "Start3")
            response = this.response
            streamResponse = response!!.toStreamResponse()
            Timber.i("DATASOURCE: %s", "Start4")
            responseByteStream = streamResponse.stream
            Timber.i("DATASOURCE: %s", "Start5")
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(
                e, dataSpec, HttpDataSourceException.TYPE_OPEN
            )
        }

        streamResponse.throwOnFailure()

        val responseCode = response.code()

        // Check for a valid response code.
        if (!response.isSuccessful) {
            if (responseCode == 416) {
                val documentSize =
                    HttpUtil.getDocumentSize(response.headers()[HttpHeaders.CONTENT_RANGE])
                if (dataSpec.position == documentSize) {
                    openedNetwork = true
                    transferStarted(dataSpec)
                    return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else 0
                }
            }
            val errorResponseBody: ByteArray = try {
                Util.toByteArray(Assertions.checkNotNull(responseByteStream))
            } catch (e: IOException) {
                Util.EMPTY_BYTE_ARRAY
            }
            val headers = response.headers().toMultimap()
            closeConnectionQuietly()
            val cause: IOException? =
                if (responseCode == 416) DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) else null
            throw InvalidResponseCodeException(
                responseCode, response.message(), cause, headers, dataSpec, errorResponseBody
            )
        }

        Timber.i("DATASOURCE: %s", "Start6")

        // If we requested a range starting from a non-zero position and received a 200 rather than a
        // 206, then the server does not support partial requests. We'll need to manually skip to the
        // requested position.
        val bytesToSkip =
            if (responseCode == 200 && dataSpec.position != 0L) dataSpec.position else 0

        // Determine the length of the data to be read, after skipping.
        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            val contentLength = response.body()!!.contentLength()
            if (contentLength != -1L) contentLength - bytesToSkip else C.LENGTH_UNSET.toLong()
        }
        openedNetwork = true
        transferStarted(dataSpec)
        try {
            skipFully(bytesToSkip, dataSpec)
        } catch (e: HttpDataSourceException) {
            closeConnectionQuietly()
            throw e
        }
        Timber.i("DATASOURCE: %s", "Start7")

        return bytesToRead
    }

    @Throws(HttpDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            readInternal(buffer, offset, length)
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(
                e, Util.castNonNull(dataSpec), HttpDataSourceException.TYPE_READ
            )
        }
    }

    override fun close() {
        if (openedNetwork) {
            openedNetwork = false
            transferEnded()
            closeConnectionQuietly()
        } else if (openedFile) {
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

        cacheFile = Storage.getFromPath(filePath)!!
        responseByteStream = cacheFile!!.getFileInputStream()

        return cacheFile!!.getDocumentFileDescriptor("r")!!.length
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

    /**
     * Reads up to `length` bytes of data and stores them into `buffer`, starting at index
     * `offset`.
     *
     *
     * This method blocks until at least one byte of data can be read, the end of the opened range
     * is detected, or an exception is thrown.
     *
     * @param buffer The buffer into which the read data should be stored.
     * @param offset The start offset into `buffer` at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or [C.RESULT_END_OF_INPUT] if the end of the opened
     * range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    @Throws(IOException::class)
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
        // bytesTransferred(read)
        return read
    }

    /** Closes the current connection quietly, if there is one.  */
    private fun closeConnectionQuietly() {
        if (response != null) {
            Assertions.checkNotNull(response!!.body()).close()
            response = null
        }
        responseByteStream = null
    }

    init {
        this.userAgent = userAgent
        this.cacheControl = cacheControl
        this.defaultRequestProperties = defaultRequestProperties
        requestProperties = RequestProperties()
    }
}
