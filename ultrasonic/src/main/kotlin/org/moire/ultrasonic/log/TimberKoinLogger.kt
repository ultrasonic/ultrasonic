package org.moire.ultrasonic.log

import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import timber.log.Timber

/**
 * Timber Logger implementation for Koin
 */
class TimberKoinLogger(level: Level = Level.INFO) : Logger(level) {

    override fun log(level: Level, msg: MESSAGE) {
        if (this.level <= level) {
            logOnLevel(msg, level)
        }
    }

    private fun logOnLevel(msg: MESSAGE, level: Level) {
        when (level) {
            Level.DEBUG -> Timber.d(msg)
            Level.INFO -> Timber.i(msg)
            Level.ERROR -> Timber.e(msg)
            else -> Timber.e(msg)
        }
    }
}
