/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.service;

import android.content.Context;
import android.os.Handler;
import timber.log.Timber;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.JukeboxStatus;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Provides an asynchronous interface to the remote jukebox on the Subsonic server.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class JukeboxMediaPlayer
{
	private static final long STATUS_UPDATE_INTERVAL_SECONDS = 5L;

	private final TaskQueue tasks = new TaskQueue();
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> statusUpdateFuture;
	private final AtomicLong timeOfLastUpdate = new AtomicLong();
	private JukeboxStatus jukeboxStatus;
	private float gain = 0.5f;
	private VolumeToast volumeToast;
	private final AtomicBoolean running = new AtomicBoolean();
	private Thread serviceThread;
	private boolean enabled = false;
	private final Context context;

	// TODO: These create circular references, try to refactor
	private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
	private final Downloader downloader;

	// TODO: Report warning if queue fills up.
	// TODO: Create shutdown method?
	// TODO: Disable repeat.
	// TODO: Persist RC state?
	// TODO: Minimize status updates.

	public JukeboxMediaPlayer(Context context, Downloader downloader)
	{
		this.context = context;
		this.downloader = downloader;
	}

	public void startJukeboxService()
	{
		if (running.get())
		{
			return;
		}

		running.set(true);
		startProcessTasks();
		Timber.d("Started Jukebox Service");
	}

	public void stopJukeboxService()
	{
		running.set(false);
		Util.sleepQuietly(1000);

		if (serviceThread != null)
		{
			serviceThread.interrupt();
		}
		Timber.d("Stopped Jukebox Service");
	}

	private void startProcessTasks()
	{
		serviceThread = new Thread()
		{
			@Override
			public void run()
			{
				processTasks();
			}
		};

		serviceThread.start();
	}

	private synchronized void startStatusUpdate()
	{
		stopStatusUpdate();

		Runnable updateTask = new Runnable()
		{
			@Override
			public void run()
			{
				tasks.remove(GetStatus.class);
				tasks.add(new GetStatus());
			}
		};

		statusUpdateFuture = executorService.scheduleWithFixedDelay(updateTask, STATUS_UPDATE_INTERVAL_SECONDS, STATUS_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	private synchronized void stopStatusUpdate()
	{
		if (statusUpdateFuture != null)
		{
			statusUpdateFuture.cancel(false);
			statusUpdateFuture = null;
		}
	}

	private void processTasks()
	{
		while (running.get())
		{
			JukeboxTask task = null;

			try
			{
				if (!ActiveServerProvider.Companion.isOffline())
				{
					task = tasks.take();
					JukeboxStatus status = task.execute();
					onStatusUpdate(status);
				}
			}
			catch (InterruptedException ignored)
			{

			}
			catch (Throwable x)
			{
				onError(task, x);
			}

			Util.sleepQuietly(1);
		}
	}

	private void onStatusUpdate(JukeboxStatus jukeboxStatus)
	{
		timeOfLastUpdate.set(System.currentTimeMillis());
		this.jukeboxStatus = jukeboxStatus;

		// Track change?
		Integer index = jukeboxStatus.getCurrentPlayingIndex();

		if (index != null && index != -1 && index != downloader.getCurrentPlayingIndex())
		{
			mediaPlayerControllerLazy.getValue().setCurrentPlaying(index);
		}
	}

	private void onError(JukeboxTask task, Throwable x)
	{
		if (x instanceof ApiNotSupportedException && !(task instanceof Stop))
		{
			disableJukeboxOnError(x, R.string.download_jukebox_server_too_old);
		}
		else if (x instanceof OfflineException && !(task instanceof Stop))
		{
			disableJukeboxOnError(x, R.string.download_jukebox_offline);
		}
		else if (x instanceof SubsonicRESTException && ((SubsonicRESTException) x).getCode() == 50 && !(task instanceof Stop))
		{
			disableJukeboxOnError(x, R.string.download_jukebox_not_authorized);
		}
		else
		{
			Timber.e(x, "Failed to process jukebox task");
		}
	}

	private void disableJukeboxOnError(Throwable x, final int resourceId)
	{
		Timber.w(x.toString());

		new Handler().post(new Runnable()
		{
			@Override
			public void run()
			{
				Util.toast(context, resourceId, false);
			}
		});

		mediaPlayerControllerLazy.getValue().setJukeboxEnabled(false);
	}

	public void updatePlaylist()
	{
		if (!enabled) return;

		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);

		List<String> ids = new ArrayList<>();
		for (DownloadFile file : downloader.getDownloads())
		{
			ids.add(file.getSong().getId());
		}

		tasks.add(new SetPlaylist(ids));
	}

	public void skip(final int index, final int offsetSeconds)
	{
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);

		startStatusUpdate();

		if (jukeboxStatus != null)
		{
			jukeboxStatus.setPositionSeconds(offsetSeconds);
		}

		tasks.add(new Skip(index, offsetSeconds));
		mediaPlayerControllerLazy.getValue().setPlayerState(PlayerState.STARTED);
	}

	public void stop()
	{
		tasks.remove(Stop.class);
		tasks.remove(Start.class);

		stopStatusUpdate();

		tasks.add(new Stop());
	}

	public void start()
	{
		tasks.remove(Stop.class);
		tasks.remove(Start.class);

		startStatusUpdate();
		tasks.add(new Start());
	}

	public synchronized void adjustVolume(boolean up)
	{
		float delta = up ? 0.05f : -0.05f;
		gain += delta;
		gain = Math.max(gain, 0.0f);
		gain = Math.min(gain, 1.0f);

		tasks.remove(SetGain.class);
		tasks.add(new SetGain(gain));

		if (volumeToast == null) volumeToast = new VolumeToast(context);

		volumeToast.setVolume(gain);
	}

	private MusicService getMusicService()
	{
		return MusicServiceFactory.getMusicService();
	}

	public int getPositionSeconds()
	{
		if (jukeboxStatus == null || jukeboxStatus.getPositionSeconds() == null || timeOfLastUpdate.get() == 0)
		{
			return 0;
		}

		if (jukeboxStatus.isPlaying())
		{
			int secondsSinceLastUpdate = (int) ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L);
			return jukeboxStatus.getPositionSeconds() + secondsSinceLastUpdate;
		}

		return jukeboxStatus.getPositionSeconds();
	}

	public void setEnabled(boolean enabled)
	{
		Timber.d("Jukebox Service setting enabled to %b", enabled);
		this.enabled = enabled;

		tasks.clear();
		if (enabled)
		{
			updatePlaylist();
		}

		stop();
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	private static class TaskQueue
	{
		private final LinkedBlockingQueue<JukeboxTask> queue = new LinkedBlockingQueue<>();

		void add(JukeboxTask jukeboxTask)
		{
			queue.add(jukeboxTask);
		}

		JukeboxTask take() throws InterruptedException
		{
			return queue.take();
		}

		void remove(Class<? extends JukeboxTask> taskClass)
		{
			try
			{
				Iterator<JukeboxTask> iterator = queue.iterator();

				while (iterator.hasNext())
				{
					JukeboxTask task = iterator.next();

					if (taskClass.equals(task.getClass()))
					{
						iterator.remove();
					}
				}
			}
			catch (Throwable x)
			{
				Timber.w(x, "Failed to clean-up task queue.");
			}
		}

		void clear()
		{
			queue.clear();
		}
	}

	private abstract static class JukeboxTask
	{
		abstract JukeboxStatus execute() throws Exception;

		@NotNull
		@Override
		public String toString()
		{
			return getClass().getSimpleName();
		}
	}

	private class GetStatus extends JukeboxTask
	{
		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().getJukeboxStatus();
		}
	}

	private class SetPlaylist extends JukeboxTask
	{
		private final List<String> ids;

		SetPlaylist(List<String> ids)
		{
			this.ids = ids;
		}

		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().updateJukeboxPlaylist(ids);
		}
	}

	private class Skip extends JukeboxTask
	{
		private final int index;
		private final int offsetSeconds;

		Skip(int index, int offsetSeconds)
		{
			this.index = index;
			this.offsetSeconds = offsetSeconds;
		}

		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().skipJukebox(index, offsetSeconds);
		}
	}

	private class Stop extends JukeboxTask
	{
		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().stopJukebox();
		}
	}

	private class Start extends JukeboxTask
	{
		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().startJukebox();
		}
	}

	private class SetGain extends JukeboxTask
	{

		private final float gain;

		private SetGain(float gain)
		{
			this.gain = gain;
		}

		@Override
		JukeboxStatus execute() throws Exception
		{
			return getMusicService().setJukeboxGain(gain);
		}
	}

	private static class VolumeToast extends Toast
	{

		private final ProgressBar progressBar;

		public VolumeToast(Context context)
		{
			super(context);
			setDuration(Toast.LENGTH_SHORT);
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View view = inflater.inflate(R.layout.jukebox_volume, null);
			progressBar = (ProgressBar) view.findViewById(R.id.jukebox_volume_progress_bar);
			setView(view);
			setGravity(Gravity.TOP, 0, 0);
		}

		public void setVolume(float volume)
		{
			progressBar.setProgress(Math.round(100 * volume));
			show();
		}
	}
}
