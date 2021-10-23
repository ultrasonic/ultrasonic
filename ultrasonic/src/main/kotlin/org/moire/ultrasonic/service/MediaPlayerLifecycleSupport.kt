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
import android.os.Build
import android.view.KeyEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.MediaSessionEventDistributor
import org.moire.ultrasonic.util.MediaSessionEventListener
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * This class is responsible for handling received events for the Media Player implementation
 *
 * @author Sindre Mehus
 */
class MediaPlayerLifecycleSupport : KoinComponent {
    private val playbackStateSerializer by inject<PlaybackStateSerializer>()
    private val mediaPlayerController by inject<MediaPlayerController>()
    private val downloader by inject<Downloader>()
    private val mediaSessionEventDistributor by inject<MediaSessionEventDistributor>()

    private var created = false
    private var headsetEventReceiver: BroadcastReceiver? = null
    private lateinit var mediaSessionEventListener: MediaSessionEventListener

    fun onCreate() {
        onCreate(false, null)
    }

    private fun onCreate(autoPlay: Boolean, afterCreated: Runnable?) {

        if (created) {
            afterCreated?.run()
            return
        }

        mediaSessionEventListener = object : MediaSessionEventListener {
            override fun onMediaButtonEvent(keyEvent: KeyEvent?) {
                if (keyEvent != null) handleKeyEvent(keyEvent)
            }
        }

        mediaSessionEventDistributor.subscribe(mediaSessionEventListener)
        registerHeadsetReceiver()
        mediaPlayerController.onCreate()
        if (autoPlay) mediaPlayerController.preload()

        playbackStateSerializer.deserialize {

            mediaPlayerController.restore(
                it!!.songs,
                it.currentPlayingIndex,
                it.currentPlayingPosition,
                autoPlay,
                false
            )

            // Work-around: Serialize again, as the restore() method creates a
            // serialization without current playing info.
            playbackStateSerializer.serialize(
                downloader.playlist,
                downloader.currentPlayingIndex,
                mediaPlayerController.playerPosition
            )
            afterCreated?.run()
        }

        CacheCleaner().clean()
        created = true
        Timber.i("LifecycleSupport created")
    }

    fun onDestroy() {

        if (!created) return

        playbackStateSerializer.serializeNow(
            downloader.playlist,
            downloader.currentPlayingIndex,
            mediaPlayerController.playerPosition
        )

        mediaSessionEventDistributor.unsubscribe(mediaSessionEventListener)

        mediaPlayerController.clear(false)
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
                event?.let { handleKeyEvent(it) }
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

        val sp = Settings.preferences
        val context = applicationContext()
        val spKey = context
            .getString(R.string.settings_playback_resume_play_on_headphones_plug)

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
                        sp.getBoolean(
                            spKey,
                            false
                        ) && mediaPlayerController.playerState === PlayerState.PAUSED
                    ) {
                        mediaPlayerController.start()
                    }
                }
            }
        }

        val headsetIntentFilter: IntentFilter =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
            } else {
                IntentFilter(Intent.ACTION_HEADSET_PLUG)
            }

        applicationContext().registerReceiver(headsetEventReceiver, headsetIntentFilter)
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private fun handleKeyEvent(event: KeyEvent) {

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return

        val keyCode: Int
        val receivedKeyCode = event.keyCode

        // Translate PLAY and PAUSE codes to PLAY_PAUSE to improve compatibility with old Bluetooth devices
        keyCode = if (Settings.singleButtonPlayPause && (
            receivedKeyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                receivedKeyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
            )
        ) {
            Timber.i("Single button Play/Pause is set, rewriting keyCode to PLAY_PAUSE")
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        } else receivedKeyCode

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

                KeyEvent.KEYCODE_MEDIA_PLAY ->
                    if (mediaPlayerController.playerState === PlayerState.IDLE) {
                        mediaPlayerController.play()
                    } else if (mediaPlayerController.playerState !== PlayerState.STARTED) {
                        mediaPlayerController.start()
                    }

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
    private fun handleUltrasonicIntent(intentAction: String) {

        val isRunning = created

        // If Ultrasonic is not running, do nothing to stop or pause
        if (
            !isRunning && (
                intentAction == Constants.CMD_PAUSE ||
                    intentAction == Constants.CMD_STOP
                )
        ) return

        val autoStart =
            intentAction == Constants.CMD_PLAY ||
                intentAction == Constants.CMD_RESUME_OR_PLAY ||
                intentAction == Constants.CMD_TOGGLEPAUSE ||
                intentAction == Constants.CMD_PREVIOUS ||
                intentAction == Constants.CMD_NEXT

        // We can receive intents when everything is stopped, so we need to start
        onCreate(autoStart) {
            when (intentAction) {
                Constants.CMD_PLAY -> mediaPlayerController.play()
                Constants.CMD_RESUME_OR_PLAY ->
                    // If Ultrasonic wasn't running, the autoStart is enough to resume,
                    // no need to call anything
                    if (isRunning) mediaPlayerController.resumeOrPlay()

                Constants.CMD_NEXT -> mediaPlayerController.next()
                Constants.CMD_PREVIOUS -> mediaPlayerController.previous()
                Constants.CMD_TOGGLEPAUSE -> mediaPlayerController.togglePlayPause()

                Constants.CMD_STOP -> {
                    // TODO: There is a stop() function, shouldn't we use that?
                    mediaPlayerController.pause()
                    mediaPlayerController.seekTo(0)
                }
                Constants.CMD_PAUSE -> mediaPlayerController.pause()
            }
        }
    }
}
