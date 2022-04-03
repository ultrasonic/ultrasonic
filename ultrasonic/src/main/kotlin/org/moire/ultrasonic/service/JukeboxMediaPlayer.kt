/*
 * JukeboxMediaPlayer.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.content.Context
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Util.sleepQuietly
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Provides an asynchronous interface to the remote jukebox on the Subsonic server.
 *
 * TODO: Report warning if queue fills up.
 * TODO: Create shutdown method?
 * TODO: Disable repeat.
 * TODO: Persist RC state?
 * TODO: Minimize status updates.
 */
class JukeboxMediaPlayer(private val downloader: Downloader) {
    private val tasks = TaskQueue()
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var statusUpdateFuture: ScheduledFuture<*>? = null
    private val timeOfLastUpdate = AtomicLong()
    private var jukeboxStatus: JukeboxStatus? = null
    private var gain = 0.5f
    private var volumeToast: VolumeToast? = null
    private val running = AtomicBoolean()
    private var serviceThread: Thread? = null
    private var enabled = false

    // TODO: These create circular references, try to refactor
    private val mediaPlayerControllerLazy = inject<MediaPlayerController>(
        MediaPlayerController::class.java
    )

    fun startJukeboxService() {
        if (running.get()) {
            return
        }
        running.set(true)
        startProcessTasks()
        Timber.d("Started Jukebox Service")
    }

    fun stopJukeboxService() {
        running.set(false)
        sleepQuietly(1000)
        if (serviceThread != null) {
            serviceThread!!.interrupt()
        }
        Timber.d("Stopped Jukebox Service")
    }

    private fun startProcessTasks() {
        serviceThread = object : Thread() {
            override fun run() {
                processTasks()
            }
        }
        (serviceThread as Thread).start()
    }

    @Synchronized
    private fun startStatusUpdate() {
        stopStatusUpdate()
        val updateTask = Runnable {
            tasks.remove(GetStatus::class.java)
            tasks.add(GetStatus())
        }
        statusUpdateFuture = executorService.scheduleWithFixedDelay(
            updateTask,
            STATUS_UPDATE_INTERVAL_SECONDS,
            STATUS_UPDATE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    @Synchronized
    private fun stopStatusUpdate() {
        if (statusUpdateFuture != null) {
            statusUpdateFuture!!.cancel(false)
            statusUpdateFuture = null
        }
    }

    private fun processTasks() {
        while (running.get()) {
            var task: JukeboxTask? = null
            try {
                if (!isOffline()) {
                    task = tasks.take()
                    val status = task.execute()
                    onStatusUpdate(status)
                }
            } catch (ignored: InterruptedException) {
            } catch (x: Throwable) {
                onError(task, x)
            }
            sleepQuietly(1)
        }
    }

    private fun onStatusUpdate(jukeboxStatus: JukeboxStatus) {
        timeOfLastUpdate.set(System.currentTimeMillis())
        this.jukeboxStatus = jukeboxStatus
    }

    private fun onError(task: JukeboxTask?, x: Throwable) {
        if (x is ApiNotSupportedException && task !is Stop) {
            disableJukeboxOnError(x, R.string.download_jukebox_server_too_old)
        } else if (x is OfflineException && task !is Stop) {
            disableJukeboxOnError(x, R.string.download_jukebox_offline)
        } else if (x is SubsonicRESTException && x.code == 50 && task !is Stop) {
            disableJukeboxOnError(x, R.string.download_jukebox_not_authorized)
        } else {
            Timber.e(x, "Failed to process jukebox task")
        }
    }

    private fun disableJukeboxOnError(x: Throwable, resourceId: Int) {
        Timber.w(x.toString())
        val context = applicationContext()
        Handler().post { toast(context, resourceId, false) }
        mediaPlayerControllerLazy.value.isJukeboxEnabled = false
    }

    fun updatePlaylist() {
        if (!enabled) return
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        val ids: MutableList<String> = ArrayList()
        for (file in downloader.all) {
            ids.add(file.track.id)
        }
        tasks.add(SetPlaylist(ids))
    }

    fun skip(index: Int, offsetSeconds: Int) {
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        if (jukeboxStatus != null) {
            jukeboxStatus!!.positionSeconds = offsetSeconds
        }
        tasks.add(Skip(index, offsetSeconds))
    }

    fun stop() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        stopStatusUpdate()
        tasks.add(Stop())
    }

    fun start() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        tasks.add(Start())
    }

    @Synchronized
    fun adjustVolume(up: Boolean) {
        val delta = if (up) 0.05f else -0.05f
        gain += delta
        gain = gain.coerceAtLeast(0.0f)
        gain = gain.coerceAtMost(1.0f)
        tasks.remove(SetGain::class.java)
        tasks.add(SetGain(gain))
        val context = applicationContext()
        if (volumeToast == null) volumeToast = VolumeToast(context)
        volumeToast!!.setVolume(gain)
    }

    private val musicService: MusicService
        get() = getMusicService()

    val positionSeconds: Int
        get() {
            if (jukeboxStatus == null || jukeboxStatus!!.positionSeconds == null || timeOfLastUpdate.get() == 0L) {
                return 0
            }
            if (jukeboxStatus!!.isPlaying) {
                val secondsSinceLastUpdate =
                    ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L).toInt()
                return jukeboxStatus!!.positionSeconds!! + secondsSinceLastUpdate
            }
            return jukeboxStatus!!.positionSeconds!!
        }

    var isEnabled: Boolean
        set(enabled) {
            Timber.d("Jukebox Service setting enabled to %b", enabled)
            this.enabled = enabled
            tasks.clear()
            if (enabled) {
                updatePlaylist()
            }
            stop()
        }
        get() {
            return enabled
        }

    private class TaskQueue {
        private val queue = LinkedBlockingQueue<JukeboxTask>()
        fun add(jukeboxTask: JukeboxTask) {
            queue.add(jukeboxTask)
        }

        @Throws(InterruptedException::class)
        fun take(): JukeboxTask {
            return queue.take()
        }

        fun remove(taskClass: Class<out JukeboxTask?>) {
            try {
                val iterator = queue.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    if (taskClass == task.javaClass) {
                        iterator.remove()
                    }
                }
            } catch (x: Throwable) {
                Timber.w(x, "Failed to clean-up task queue.")
            }
        }

        fun clear() {
            queue.clear()
        }
    }

    private abstract class JukeboxTask {
        @Throws(Exception::class)
        abstract fun execute(): JukeboxStatus
        override fun toString(): String {
            return javaClass.simpleName
        }
    }

    private inner class GetStatus : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.getJukeboxStatus()
        }
    }

    private inner class SetPlaylist(private val ids: List<String>) :
        JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.updateJukeboxPlaylist(ids)
        }
    }

    private inner class Skip(
        private val index: Int,
        private val offsetSeconds: Int
    ) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.skipJukebox(index, offsetSeconds)
        }
    }

    private inner class Stop : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.stopJukebox()
        }
    }

    private inner class Start : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.startJukebox()
        }
    }

    private inner class SetGain(private val gain: Float) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus {
            return musicService.setJukeboxGain(gain)
        }
    }

    private class VolumeToast(context: Context) : Toast(context) {
        private val progressBar: ProgressBar
        fun setVolume(volume: Float) {
            progressBar.progress = (100 * volume).roundToInt()
            show()
        }

        init {
            duration = LENGTH_SHORT
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.jukebox_volume, null)
            progressBar = view.findViewById<View>(R.id.jukebox_volume_progress_bar) as ProgressBar
            setView(view)
            setGravity(Gravity.TOP, 0, 0)
        }
    }

    companion object {
        private const val STATUS_UPDATE_INTERVAL_SECONDS = 5L
    }
}