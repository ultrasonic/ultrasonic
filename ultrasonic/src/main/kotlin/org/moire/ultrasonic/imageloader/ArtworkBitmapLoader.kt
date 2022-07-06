/*
 * ArtworkBitmapLoader.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.session.BitmapLoader
import com.google.common.base.Suppliers
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors

class ArtworkBitmapLoader : BitmapLoader {
    private val DEFAULT_EXECUTOR_SERVICE = Suppliers.memoize {
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor()
        )
    }

    private val executorService: ListeningExecutorService by lazy {
        DEFAULT_EXECUTOR_SERVICE.get()
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executorService.submit<Bitmap> {
            decode(
                data
            )
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executorService.submit<Bitmap> {
            load(uri)
        }
    }

    private fun decode(data: ByteArray): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return bitmap ?: throw IllegalArgumentException("Could not decode bitmap")
    }

    @Throws(IOException::class)
    private fun load(uri: Uri): Bitmap {
        return BitmapFactory.decodeFile(uri.path)
    }
}
