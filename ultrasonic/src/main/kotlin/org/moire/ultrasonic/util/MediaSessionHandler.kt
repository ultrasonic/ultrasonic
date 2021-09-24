/*
 * MediaSessionHandler.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
import android.view.KeyEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.PlayerState
import org.moire.ultrasonic.imageloader.BitmapUtils
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver
import org.moire.ultrasonic.service.DownloadFile
import timber.log.Timber

private const val INTENT_CODE_MEDIA_BUTTON = 161
private const val CALL_DIVIDE = 10
/**
 * Central place to handle the state of the MediaSession
 */
class MediaSessionHandler : KoinComponent {

    private var mediaSession: MediaSessionCompat? = null
    private var playbackState: Int? = null
    private var playbackActions: Long? = null
    private var cachedPlayingIndex: Long? = null

    private val mediaSessionEventDistributor by inject<MediaSessionEventDistributor>()
    private val applicationContext by inject<Context>()

    private var referenceCount: Int = 0
    private var cachedPlaylist: List<MediaSessionCompat.QueueItem>? = null
    private var playbackPositionDelayCount: Int = 0
    private var cachedPosition: Long = 0

    fun release() {

        if (referenceCount > 0) referenceCount--
        if (referenceCount > 0) return

        mediaSession?.isActive = false
        mediaSessionEventDistributor.releaseCachedMediaSessionToken()
        mediaSession?.release()
        mediaSession = null

        Timber.i("MediaSessionHandler.release Media Session released")
    }

    fun initialize() {

        referenceCount++
        if (referenceCount > 1) return

        @Suppress("MagicNumber")
        val keycode = 110

        Timber.d("MediaSessionHandler.initialize Creating Media Session")

        mediaSession = MediaSessionCompat(applicationContext, "UltrasonicService")
        val mediaSessionToken = mediaSession?.sessionToken ?: return
        mediaSessionEventDistributor.raiseMediaSessionTokenCreatedEvent(mediaSessionToken)

        updateMediaButtonReceiver()

        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()

                Util.getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    keycode
                ).send()

                Timber.v("Media Session Callback: onPlay")
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                super.onPlayFromMediaId(mediaId, extras)

                Timber.d("Media Session Callback: onPlayFromMediaId %s", mediaId)
                mediaSessionEventDistributor.raisePlayFromMediaIdRequestedEvent(mediaId, extras)
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                super.onPlayFromSearch(query, extras)

                Timber.d("Media Session Callback: onPlayFromSearch %s", query)
                mediaSessionEventDistributor.raisePlayFromSearchRequestedEvent(query, extras)
            }

            override fun onPause() {
                super.onPause()
                Util.getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onPause")
            }

            override fun onStop() {
                super.onStop()
                Util.getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_STOP,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onStop")
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                Util.getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onSkipToNext")
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                Util.getPendingIntentForMediaAction(
                    applicationContext,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    keycode
                ).send()
                Timber.v("Media Session Callback: onSkipToPrevious")
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                // This probably won't be necessary once we implement more
                // of the modern media APIs, like the MediaController etc.
                val event = mediaButtonEvent.extras!!["android.intent.extra.KEY_EVENT"] as KeyEvent?
                mediaSessionEventDistributor.raiseMediaButtonEvent(event)
                return true
            }

            override fun onSkipToQueueItem(id: Long) {
                super.onSkipToQueueItem(id)
                mediaSessionEventDistributor.raiseSkipToQueueItemRequestedEvent(id)
            }
        }
        )

        // It seems to be the best practice to set this to true for the lifetime of the session
        mediaSession?.isActive = true
        if (cachedPlaylist != null) setMediaSessionQueue(cachedPlaylist)
        Timber.i("MediaSessionHandler.initialize Media Session created")
    }

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    fun updateMediaSession(
        currentPlaying: DownloadFile?,
        currentPlayingIndex: Long?,
        playerState: PlayerState
    ) {
        Timber.d("Updating the MediaSession")

        // Set Metadata
        val metadata = MediaMetadataCompat.Builder()
        if (currentPlaying != null) {
            try {
                val song = currentPlaying.song
                val cover = BitmapUtils.getAlbumArtBitmapFromDisk(
                    song, Util.getMinDisplayMetric()
                )
                val duration = song.duration?.times(1000) ?: -1
                metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            } catch (e: Exception) {
                Timber.e(e, "Error setting the metadata")
            }
        }

        // Save the metadata
        mediaSession?.setMetadata(metadata.build())

        playbackActions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
            PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM

        // Map our playerState to native PlaybackState
        // TODO: Synchronize these APIs
        when (playerState) {
            PlayerState.STARTED -> {
                playbackState = PlaybackStateCompat.STATE_PLAYING
                playbackActions = playbackActions!! or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
            }
            PlayerState.COMPLETED,
            PlayerState.STOPPED -> {
                playbackState = PlaybackStateCompat.STATE_STOPPED
                cachedPosition = PLAYBACK_POSITION_UNKNOWN
            }
            PlayerState.IDLE -> {
                // IDLE state usually just means the playback is stopped
                // STATE_NONE means that there is no track to play (playlist is empty)
                playbackState = if (currentPlaying == null)
                    PlaybackStateCompat.STATE_NONE
                else
                    PlaybackStateCompat.STATE_STOPPED
                playbackActions = 0L
                cachedPosition = PLAYBACK_POSITION_UNKNOWN
            }
            PlayerState.PAUSED -> {
                playbackState = PlaybackStateCompat.STATE_PAUSED
                playbackActions = playbackActions!! or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_STOP
            }
            else -> {
                // These are the states PREPARING, PREPARED & DOWNLOADING
                playbackState = PlaybackStateCompat.STATE_PAUSED
            }
        }

        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setState(playbackState!!, cachedPosition, 1.0f)

        // Set actions
        playbackStateBuilder.setActions(playbackActions!!)

        cachedPlayingIndex = currentPlayingIndex
        setMediaSessionQueue(cachedPlaylist)
        if (
            currentPlayingIndex != null && cachedPlaylist != null &&
            !Settings.shouldDisableNowPlayingListSending
        )
            playbackStateBuilder.setActiveQueueItemId(currentPlayingIndex)

        // Save the playback state
        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    fun updateMediaSessionQueue(playlist: Iterable<MusicDirectory.Entry>) {
        // This call is cached because Downloader may initialize earlier than the MediaSession
        cachedPlaylist = playlist.mapIndexed { id, song ->
            MediaSessionCompat.QueueItem(
                Util.getMediaDescriptionForEntry(song),
                id.toLong()
            )
        }
        setMediaSessionQueue(cachedPlaylist)
    }

    private fun setMediaSessionQueue(queue: List<MediaSessionCompat.QueueItem>?) {
        if (mediaSession == null) return
        if (Settings.shouldDisableNowPlayingListSending) return

        mediaSession?.setQueueTitle(applicationContext.getString(R.string.button_bar_now_playing))
        mediaSession?.setQueue(queue)
    }

    fun updateMediaSessionPlaybackPosition(playbackPosition: Long) {

        cachedPosition = playbackPosition
        if (mediaSession == null) return

        if (playbackState == null || playbackActions == null) return

        // Playback position is updated too frequently in the player.
        // This counter makes sure that the MediaSession is updated ~ at every second
        playbackPositionDelayCount++
        if (playbackPositionDelayCount < CALL_DIVIDE) return

        playbackPositionDelayCount = 0
        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setState(playbackState!!, playbackPosition, 1.0f)
        playbackStateBuilder.setActions(playbackActions!!)

        if (
            cachedPlayingIndex != null && cachedPlaylist != null &&
            !Settings.shouldDisableNowPlayingListSending
        )
            playbackStateBuilder.setActiveQueueItemId(cachedPlayingIndex!!)

        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    fun updateMediaButtonReceiver() {
        if (Settings.mediaButtonsEnabled) {
            registerMediaButtonEventReceiver()
        } else {
            unregisterMediaButtonEventReceiver()
        }
    }

    private fun registerMediaButtonEventReceiver() {
        val component = ComponentName(
            applicationContext.packageName,
            MediaButtonIntentReceiver::class.java.name
        )
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = component

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            INTENT_CODE_MEDIA_BUTTON,
            mediaButtonIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        mediaSession?.setMediaButtonReceiver(pendingIntent)
    }

    private fun unregisterMediaButtonEventReceiver() {
        mediaSession?.setMediaButtonReceiver(null)
    }
}
