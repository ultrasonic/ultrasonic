/*
 * APIDataSource.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.annotation.SuppressLint
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.Assertions
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
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import okhttp3.Call
import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import timber.log.Timber

/**
 * An [HttpDataSource] that delegates to Square's [Call.Factory].
 *
 *
 * Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the `dataSpec`, [.setRequestProperty] and the default parameters used to
 * construct the instance.
 */
@SuppressLint("UnsafeOptInUsageError")
@Suppress("MagicNumber")
open class APIDataSource private constructor(
    subsonicAPIClient: SubsonicAPIClient
) : BaseDataSource(true),
    HttpDataSource {

    /** [DataSource.Factory] for [APIDataSource] instances.  */
    class Factory(private var subsonicAPIClient: SubsonicAPIClient) : HttpDataSource.Factory {
        private val defaultRequestProperties: RequestProperties = RequestProperties()
        private var transferListener: TransferListener? = null

        override fun setDefaultRequestProperties(
            defaultRequestProperties: Map<String, String>
        ): Factory {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
            return this
        }

        /**
         * Sets the [TransferListener] that will be used.
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

        fun setAPIClient(newClient: SubsonicAPIClient) {
            this.subsonicAPIClient = newClient
        }

        override fun createDataSource(): APIDataSource {
            val dataSource = APIDataSource(
                subsonicAPIClient
            )
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }
    }

    private val subsonicAPIClient: SubsonicAPIClient = Assertions.checkNotNull(subsonicAPIClient)
    private val requestProperties: RequestProperties = RequestProperties()
    private var dataSpec: DataSpec? = null
    private var response: retrofit2.Response<ResponseBody>? = null
    private var responseByteStream: InputStream? = null
    private var openedNetwork = false
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0

    override fun getUri(): Uri? {
        return when (response) {
            null -> null
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

    @Suppress("LongMethod", "NestedBlockDepth")
    @Throws(HttpDataSourceException::class)
    override fun open(dataSpec: DataSpec): Long {
        Timber.i(
            "APIDatasource: Open: %s %s %s",
            dataSpec.uri,
            dataSpec.position,
            dataSpec.toString()
        )

        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = 0

        transferInitializing(dataSpec)
        val components = dataSpec.uri.toString().split('|')
        val id = components[0]
        val bitrate = components[1].toInt()
        val request = subsonicAPIClient.api.stream(id, bitrate, offset = dataSpec.position)
        val response: retrofit2.Response<ResponseBody>?
        val streamResponse: StreamResponse

        try {
            this.response = request.execute()
            response = this.response
            streamResponse = response!!.toStreamResponse()
            responseByteStream = streamResponse.stream
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
            } catch (ignore: IOException) {
                Util.EMPTY_BYTE_ARRAY
            }
            val headers = response.headers().toMultimap()
            closeConnectionQuietly()
            val cause: IOException? =
                if (responseCode == 416) DataSourceException(
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                ) else null
            throw InvalidResponseCodeException(
                responseCode, response.message(), cause, headers, dataSpec, errorResponseBody
            )
        }

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

        return bytesToRead
    }

    @Throws(HttpDataSourceException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // Timber.d("APIDatasource: Read: %s %s", offset, length)
        return try {
            readInternal(buffer, offset, length)
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(
                e, Util.castNonNull(dataSpec), HttpDataSourceException.TYPE_READ
            )
        }
    }

    override fun close() {
        Timber.i("APIDatasource: Close")
        if (openedNetwork) {
            openedNetwork = false
            transferEnded()
            closeConnectionQuietly()
        }
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
        var bytesToSkipCpy = bytesToSkip
        if (bytesToSkipCpy == 0L) {
            return
        }
        val skipBuffer = ByteArray(4096)
        try {
            while (bytesToSkipCpy > 0) {
                val readLength =
                    bytesToSkipCpy.coerceAtMost(skipBuffer.size.toLong()).toInt()
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
                bytesToSkipCpy -= read.toLong()
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
        var readLengthCpy = readLength
        if (readLengthCpy == 0) {
            return 0
        }
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLengthCpy = readLengthCpy.toLong().coerceAtMost(bytesRemaining).toInt()
        }
        val read = Util.castNonNull(responseByteStream).read(buffer, offset, readLengthCpy)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read.toLong()
        bytesTransferred(read)
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
}
