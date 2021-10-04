/*
 * CancellationToken.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

/**
 * This class contains a very simple implementation of a CancellationToken
 */
class CancellationToken {
    var isCancellationRequested: Boolean = false

    /**
     * Requests that this token be cancelled
     */
    fun cancel() {
        isCancellationRequested = true
    }
}
