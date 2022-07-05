/*
 * MediaPlayerLifecycleSupport.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.KeyEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.ifNotNull
import timber.log.Timber

/**
 * This class is responsible for handling received events for the Media Player implementation
 */
class MediaPlayerLifecycleSupport : KoinComponent {
    private val playbackStateSerializer by inject<PlaybackStateSerializer>()
    private val mediaPlayerController by inject<MediaPlayerController>()

    private var created = false
    private var headsetEventReceiver: BroadcastReceiver? = null

    fun onCreate() {
        onCreate(false, null)
    }

    private fun onCreate(autoPlay: Boolean, afterRestore: Runnable?) {

        if (created) {
            afterRestore?.run()
            return
        }

        mediaPlayerController.onCreate {
            restoreLastSession(autoPlay, afterRestore)
        }

        registerHeadsetReceiver()

        CacheCleaner().clean()
        created = true
        Timber.i("LifecycleSupport created")
    }

    private fun restoreLastSession(autoPlay: Boolean, afterRestore: Runnable?) {
        playbackStateSerializer.deserialize {

            Timber.i("Restoring %s songs", it!!.songs.size)

            mediaPlayerController.restore(
                it,
                autoPlay,
                false
            )

            afterRestore?.run()
        }
    }

    fun onDestroy() {

        if (!created) return

        playbackStateSerializer.serializeNow(
            mediaPlayerController.playList,
            mediaPlayerController.currentMediaItemIndex,
            mediaPlayerController.playerPosition,
            mediaPlayerController.isShufflePlayEnabled,
            mediaPlayerController.repeatMode
        )

        mediaPlayerController.clear(false)
        RxBus.shutdownCommandPublisher.onNext(Unit)

        applicationContext().unregisterReceiver(headsetEventReceiver)
        mediaPlayerController.onDestroy()

        created = false
        Timber.i("LifecycleSupport destroyed")
    }

    fun receiveIntent(intent: Intent?) {

        if (intent == null) return

        val intentAction = intent.action
        if (intentAction == null || intentAction.isEmpty()) return

        Timber.i("Received intent: %s", intentAction)

        if (intentAction == Constants.CMD_PROCESS_KEYCODE) {
            if (intent.extras != null) {
                val event = intent.extras!![Intent.EXTRA_KEY_EVENT] as KeyEvent?
                event.ifNotNull { handleKeyEvent(it) }
            }
        } else {
            handleUltrasonicIntent(intentAction)
        }
    }

    /**
     * The Headset Intent Receiver is responsible for resuming playback when a headset is inserted
     * and pausing it when it is removed.
     * Unfortunately this Intent can't be registered in the AndroidManifest, so it works only
     * while Ultrasonic is running.
     */
    private fun registerHeadsetReceiver() {

        headsetEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras ?: return

                Timber.i("Headset event for: %s", extras["name"])

                val state = extras.getInt("state")

                if (state == 0) {
                    if (!mediaPlayerController.isJukeboxEnabled) {
                        mediaPlayerController.pause()
                    }
                } else if (state == 1) {
                    if (!mediaPlayerController.isJukeboxEnabled &&
                        Settings.resumePlayOnHeadphonePlug && !mediaPlayerController.isPlaying
                    ) {
                        mediaPlayerController.prepare()
                        mediaPlayerController.play()
                    }
                }
            }
        }

        val headsetIntentFilter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)

        applicationContext().registerReceiver(headsetEventReceiver, headsetIntentFilter)
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private fun handleKeyEvent(event: KeyEvent) {

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return

        val keyCode: Int = event.keyCode

        val autoStart =
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT

        // We can receive intents (e.g. MediaButton) when everything is stopped, so we need to start
        onCreate(autoStart) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> mediaPlayerController.togglePlayPause()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> mediaPlayerController.previous()
                KeyEvent.KEYCODE_MEDIA_NEXT -> mediaPlayerController.next()
                KeyEvent.KEYCODE_MEDIA_STOP -> mediaPlayerController.stop()
                KeyEvent.KEYCODE_MEDIA_PLAY -> mediaPlayerController.play()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> mediaPlayerController.pause()
                KeyEvent.KEYCODE_1 -> mediaPlayerController.setSongRating(1)
                KeyEvent.KEYCODE_2 -> mediaPlayerController.setSongRating(2)
                KeyEvent.KEYCODE_3 -> mediaPlayerController.setSongRating(3)
                KeyEvent.KEYCODE_4 -> mediaPlayerController.setSongRating(4)
                KeyEvent.KEYCODE_5 -> mediaPlayerController.setSongRating(5)
                KeyEvent.KEYCODE_STAR -> mediaPlayerController.toggleSongStarred()
                else -> {
                }
            }
        }
    }

    /**
     * This function processes the intent that could come from other applications.
     */
    @Suppress("ComplexMethod")
    private fun handleUltrasonicIntent(action: String) {

        val isRunning = created

        // If Ultrasonic is not running, do nothing to stop or pause
        if (!isRunning && (action == Constants.CMD_PAUSE || action == Constants.CMD_STOP))
            return

        val autoStart = action == Constants.CMD_PLAY ||
            action == Constants.CMD_RESUME_OR_PLAY ||
            action == Constants.CMD_TOGGLEPAUSE ||
            action == Constants.CMD_PREVIOUS ||
            action == Constants.CMD_NEXT

        // We can receive intents when everything is stopped, so we need to start
        onCreate(autoStart) {
            when (action) {
                Constants.CMD_PLAY -> mediaPlayerController.play()
                Constants.CMD_RESUME_OR_PLAY ->
                    // If Ultrasonic wasn't running, the autoStart is enough to resume,
                    // no need to call anything
                    if (isRunning) mediaPlayerController.resumeOrPlay()

                Constants.CMD_NEXT -> mediaPlayerController.next()
                Constants.CMD_PREVIOUS -> mediaPlayerController.previous()
                Constants.CMD_TOGGLEPAUSE -> mediaPlayerController.togglePlayPause()
                Constants.CMD_STOP -> mediaPlayerController.stop()
                Constants.CMD_PAUSE -> mediaPlayerController.pause()
            }
        }
    }
}
