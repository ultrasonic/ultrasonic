/*
 * Dialogs.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import org.moire.ultrasonic.R

open class InfoDialog(
    context: Context,
    message: CharSequence?,
    private val activity: Activity? = null,
    private val finishActivityOnClose: Boolean = false
) {

    open var builder: AlertDialog.Builder = Builder(activity ?: context, message)

    fun show() {
        builder.setOnCancelListener {
            if (finishActivityOnClose) {
                activity!!.finish()
            }
        }
        builder.setPositiveButton(R.string.common_ok) { _, _ ->
            if (finishActivityOnClose) {
                activity!!.finish()
            }
        }
        builder.create().show()
    }

    class Builder(context: Context?) : AlertDialog.Builder(context) {

        constructor(context: Context, message: CharSequence?) : this(context) {
            setMessage(message)
        }

        init {
            setIcon(R.drawable.ic_baseline_info)
            setTitle(R.string.common_confirm)
            setCancelable(true)
            setPositiveButton(R.string.common_ok) { _, _ ->
                // Just close it
            }
        }
    }
}

class ErrorDialog(
    context: Context,
    message: CharSequence?,
    activity: Activity? = null,
    finishActivityOnClose: Boolean = false
) : InfoDialog(context, message, activity, finishActivityOnClose) {

    override var builder: AlertDialog.Builder = Builder(activity ?: context, message)

    class Builder(context: Context?) : AlertDialog.Builder(context) {
        constructor(context: Context, message: CharSequence?) : this(context) {
            setMessage(message)
        }

        init {
            setIcon(R.drawable.ic_baseline_warning)
            setTitle(R.string.error_label)
            setCancelable(true)
            setPositiveButton(R.string.common_ok) { _, _ ->
                // Just close it
            }
        }
    }
}
