/*
 * OfflineException.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

/**
 * Thrown by service methods that are not available in offline mode.
 */
class OfflineException(message: String?) : Exception(message) {
    companion object {
        private const val serialVersionUID = -4479642294747429444L
    }
}