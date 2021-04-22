package org.moire.ultrasonic.service

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util
import timber.log.Timber

class AudioFocusHandler(private val context: Context) {
    // TODO: This is a circular reference, try to remove it
    private val mediaPlayerControllerLazy = inject(MediaPlayerController::class.java)

    fun requestAudioFocus() {
        if (!hasFocus) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            hasFocus = true


            audioManager.requestAudioFocus(object : OnAudioFocusChangeListener {
                override fun onAudioFocusChange(focusChange: Int) {
                    val mediaPlayerController = mediaPlayerControllerLazy.value
                    if ((focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) && !mediaPlayerController.isJukeboxEnabled) {
                        Timber.v("Lost Audio Focus")
                        if (mediaPlayerController.playerState === PlayerState.STARTED) {
                            val preferences = Util.getPreferences(context)
                            val lossPref = preferences.getString(Constants.PREFERENCES_KEY_TEMP_LOSS, "1")!!.toInt()
                            if (lossPref == 2 || lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                lowerFocus = true
                                mediaPlayerController.setVolume(0.1f)
                            } else if (lossPref == 0 || lossPref == 1) {
                                pauseFocus = true
                                mediaPlayerController.pause()
                            }
                        }
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        Timber.v("Regained Audio Focus")
                        if (pauseFocus) {
                            pauseFocus = false
                            mediaPlayerController.start()
                        } else if (lowerFocus) {
                            lowerFocus = false
                            mediaPlayerController.setVolume(1.0f)
                        }
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS && !mediaPlayerController.isJukeboxEnabled) {
                        hasFocus = false
                        mediaPlayerController.pause()
                        audioManager.abandonAudioFocus(this)
                        Timber.v("Abandoned Audio Focus")
                    }
                }
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            Timber.v("Got Audio Focus")
        }
    }

    companion object {
        private var hasFocus = false
        private var pauseFocus = false
        private var lowerFocus = false
    }
}