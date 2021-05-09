package org.moire.ultrasonic.subsonic

import android.content.Context
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.util.Util

/**
 * Utility class for checking the availability of the network and storage
 */
class NetworkAndStorageChecker(val context: Context) {
    fun warnIfNetworkOrStorageUnavailable() {
        if (!Util.isExternalStoragePresent()) {
            Util.toast(context, R.string.select_album_no_sdcard)
        } else if (!isOffline() && !Util.isNetworkConnected(context)) {
            Util.toast(context, R.string.select_album_no_network)
        }
    }
}
