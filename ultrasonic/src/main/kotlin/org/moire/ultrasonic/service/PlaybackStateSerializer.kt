/*
 * PlaybackStateSerializer.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import timber.log.Timber

/**
 * This class is responsible for the serialization / deserialization
 * of the playlist and the player state (e.g. current playing number and play position)
 * to the filesystem.
 */
class PlaybackStateSerializer : KoinComponent {

    private val context by inject<Context>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun serialize(
        songs: Iterable<DownloadFile>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int
    ) {
        if (isSerializing.get() || !isSetup.get()) return

        isSerializing.set(true)

        ioScope.launch {
            serializeNow(songs, currentPlayingIndex, currentPlayingPosition)
        }.invokeOnCompletion {
            isSerializing.set(false)
        }
    }

    fun serializeNow(
        referencedList: Iterable<DownloadFile>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int
    ) {
        val state = State()
        val songs = referencedList.toList()

        for (downloadFile in songs) {
            state.songs.add(downloadFile.track)
        }

        state.currentPlayingIndex = currentPlayingIndex
        state.currentPlayingPosition = currentPlayingPosition

        Timber.i(
            "Serialized currentPlayingIndex: %d, currentPlayingPosition: %d",
            state.currentPlayingIndex,
            state.currentPlayingPosition
        )

        FileUtil.serialize(context, state, Constants.FILENAME_PLAYLIST_SER)
    }

    fun deserialize(afterDeserialized: (State?) -> Unit?) {
        if (isDeserializing.get()) return
        ioScope.launch {
            try {
                deserializeNow(afterDeserialized)
                isSetup.set(true)
            } catch (all: Exception) {
                Timber.e(all, "Had a problem deserializing:")
            } finally {
                isDeserializing.set(false)
            }
        }
    }

    private fun deserializeNow(afterDeserialized: (State?) -> Unit?) {

        val state = FileUtil.deserialize<State>(
            context, Constants.FILENAME_PLAYLIST_SER
        ) ?: return

        Timber.i(
            "Deserialized currentPlayingIndex: %d, currentPlayingPosition: %d ",
            state.currentPlayingIndex,
            state.currentPlayingPosition
        )

        mainScope.launch {
            afterDeserialized(state)
        }
    }

    companion object {
        private val isSetup = AtomicBoolean(false)
        private val isSerializing = AtomicBoolean(false)
        private val isDeserializing = AtomicBoolean(false)
    }
}
