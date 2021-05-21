package org.moire.ultrasonic.subsonic

import android.content.Context
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.Util

/**
 * This utility class helps starting video playback
 */
class VideoPlayer() {
    fun playVideo(context: Context, entry: MusicDirectory.Entry?) {
        if (!Util.isNetworkConnected()) {
            Util.toast(context, R.string.select_album_no_network)
            return
        }
        val player = Util.getVideoPlayerType()
        try {
            player.playVideo(context, entry)
        } catch (e: Exception) {
            Util.toast(context, e.toString(), false)
        }
    }
}
