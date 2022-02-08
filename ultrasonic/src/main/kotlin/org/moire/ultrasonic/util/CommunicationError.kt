/*
 * CommunicationErrorUtil.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.fasterxml.jackson.core.JsonParseException
import java.io.FileNotFoundException
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.subsonic.getLocalizedErrorMessage
import timber.log.Timber

/**
 * Contains helper functions to handle the exceptions
 * thrown during the communication with a Subsonic server
 */
object CommunicationError {
    fun getHandler(context: Context?, handler: ((CoroutineContext, Throwable) -> Unit)? = null):
        CoroutineExceptionHandler {
        return CoroutineExceptionHandler { coroutineContext, exception ->
            Handler(Looper.getMainLooper()).post {
                handleError(exception, context)
                handler?.invoke(coroutineContext, exception)
            }
        }
    }

    @JvmStatic
    fun handleError(error: Throwable?, context: Context?) {
        Timber.w(error)

        if (context == null) return

        ErrorDialog(
            context = context,
            message = getErrorMessage(error!!, context)
        ).show()
    }

    @JvmStatic
    @Suppress("ReturnCount")
    fun getErrorMessage(error: Throwable, context: Context?): String {
        if (context == null) return "Couldn't get Error message, Context is null"
        if (error is IOException && !Util.isNetworkConnected()) {
            return context.resources.getString(R.string.background_task_no_network)
        } else if (error is FileNotFoundException) {
            return context.resources.getString(R.string.background_task_not_found)
        } else if (error is JsonParseException) {
            return context.resources.getString(R.string.background_task_parse_error)
        } else if (error is SSLException) {
            return if (
                error.cause is CertificateException &&
                error.cause?.cause is CertPathValidatorException
            ) {
                context.resources
                    .getString(
                        R.string.background_task_ssl_cert_error, error.cause?.cause?.message
                    )
            } else {
                context.resources.getString(R.string.background_task_ssl_error)
            }
        } else if (error is ApiNotSupportedException) {
            return context.resources.getString(
                R.string.background_task_unsupported_api, error.serverApiVersion
            )
        } else if (error is IOException) {
            return context.resources.getString(R.string.background_task_network_error)
        } else if (error is SubsonicRESTException) {
            return error.getLocalizedErrorMessage(context)
        }
        val message = error.message
        return message ?: error.javaClass.simpleName
    }
}
