package org.moire.ultrasonic.subsonic

import android.content.Context
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.Util

class VideoPlayer(val context: Context) {
    fun playVideo(entry: MusicDirectory.Entry?) {
        if (!Util.isNetworkConnected(context)) {
            Util.toast(context, R.string.select_album_no_network)
            return
        }
        val player = Util.getVideoPlayerType(context)
        try {
            player.playVideo(context, entry)
        } catch (e: Exception) {
            Util.toast(context, e.message, false)
        }
    }
}