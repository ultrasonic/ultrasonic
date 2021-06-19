/*
 * SilentBackgroundTask.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.Activity

/**
 * @author Sindre Mehus
 */
abstract class SilentBackgroundTask<T>(activity: Activity?) : BackgroundTask<T>(activity) {
    override fun execute() {
        val thread: Thread = object : Thread() {
            override fun run() {
                try {
                    val result = doInBackground()
                    handler.post { done(result) }
                } catch (all: Throwable) {
                    handler.post { error(all) }
                }
            }
        }
        thread.start()
    }

    override fun updateProgress(messageId: Int) {}
    override fun updateProgress(message: String) {}
}
