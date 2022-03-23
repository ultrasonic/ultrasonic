package org.moire.ultrasonic.subsonic

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/**
 * This utility class helps starting video playback
 */
@Suppress("UtilityClassWithPublicConstructor")
class VideoPlayer {
    companion object {
        fun playVideo(context: Context, track: MusicDirectory.Track?) {
            if (!Util.isNetworkConnected() || track == null) {
                Util.toast(context, R.string.select_album_no_network)
                return
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                val url = MusicServiceFactory.getMusicService().getVideoUrl(track.id)
                intent.setDataAndType(
                    Uri.parse(url),
                    "video/*"
                )
                context.startActivity(intent)
            } catch (all: Exception) {
                Util.toast(context, all.toString(), false)
            }
        }
    }
}
