/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2020 (C) Jozsef Varga
 */
package org.moire.ultrasonic.service

import android.app.AlertDialog
import android.content.Context
import com.fasterxml.jackson.core.JsonParseException
import java.io.FileNotFoundException
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.subsonic.getLocalizedErrorMessage
import org.moire.ultrasonic.util.Util
import timber.log.Timber

class CommunicationErrorHandler {
    companion object {
        fun handleError(error: Throwable?, context: Context) {
            Timber.w(error)

            AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.error_label)
                .setMessage(getErrorMessage(error!!, context))
                .setCancelable(true)
                .setPositiveButton(R.string.common_ok) { _, _ -> }
                .create().show()
        }

        fun getErrorMessage(error: Throwable, context: Context): String {
            if (error is IOException && !Util.isNetworkConnected(context)) {
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
}
