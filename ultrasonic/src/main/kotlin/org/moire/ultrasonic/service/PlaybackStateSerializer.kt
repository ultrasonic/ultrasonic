/*
 * PlaybackStateSerializer.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.MediaSessionHandler
import timber.log.Timber

/**
 * This class is responsible for the serialization / deserialization
 * of the playlist and the player state (e.g. current playing number and play position)
 * to the filesystem.
 */
class PlaybackStateSerializer : KoinComponent {

    private val context by inject<Context>()
    private val mediaSessionHandler by inject<MediaSessionHandler>()

    val lock: Lock = ReentrantLock()
    val setup = AtomicBoolean(false)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun serialize(
        songs: Iterable<DownloadFile>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int
    ) {
        if (!setup.get()) return

        appScope.launch {
            if (lock.tryLock()) {
                try {
                    serializeNow(songs, currentPlayingIndex, currentPlayingPosition)
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    fun serializeNow(
        songs: Iterable<DownloadFile>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int
    ) {
        val state = State()

        for (downloadFile in songs) {
            state.songs.add(downloadFile.song)
        }

        state.currentPlayingIndex = currentPlayingIndex
        state.currentPlayingPosition = currentPlayingPosition

        Timber.i(
            "Serialized currentPlayingIndex: %d, currentPlayingPosition: %d",
            state.currentPlayingIndex,
            state.currentPlayingPosition
        )

        FileUtil.serialize(context, state, Constants.FILENAME_PLAYLIST_SER)

        // This is called here because the queue is usually serialized after a change
        mediaSessionHandler.updateMediaSessionQueue(state.songs)
    }

    fun deserialize(afterDeserialized: Consumer<State?>) {

        appScope.launch {
            try {
                lock.lock()
                deserializeNow(afterDeserialized)
                setup.set(true)
            } finally {
                lock.unlock()
            }
        }
    }

    private fun deserializeNow(afterDeserialized: Consumer<State?>) {

        val state = FileUtil.deserialize<State>(
            context, Constants.FILENAME_PLAYLIST_SER
        ) ?: return

        Timber.i(
            "Deserialized currentPlayingIndex: %d, currentPlayingPosition: %d ",
            state.currentPlayingIndex,
            state.currentPlayingPosition
        )

        mediaSessionHandler.updateMediaSessionQueue(state.songs)
        afterDeserialized.accept(state)
    }
}
