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
package org.moire.ultrasonic.util;

import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class ShufflePlayBuffer
{
	private static final int CAPACITY = 50;
	private static final int REFILL_THRESHOLD = 40;

	private final List<MusicDirectory.Entry> buffer = new ArrayList<>();
	private ScheduledExecutorService executorService;
	private int currentServer;

	public boolean isEnabled = false;

	public ShufflePlayBuffer()
	{
	}

	public void onCreate()
	{
		executorService = Executors.newSingleThreadScheduledExecutor();
		Runnable runnable = this::refill;
		executorService.scheduleWithFixedDelay(runnable, 1, 10, TimeUnit.SECONDS);
		Timber.i("ShufflePlayBuffer created");
	}

	public void onDestroy()
	{
		executorService.shutdown();
		Timber.i("ShufflePlayBuffer destroyed");
	}

	public List<MusicDirectory.Entry> get(int size)
	{
		clearBufferIfNecessary();

		List<MusicDirectory.Entry> result = new ArrayList<>(size);
		synchronized (buffer)
		{
			while (!buffer.isEmpty() && result.size() < size)
			{
				result.add(buffer.remove(buffer.size() - 1));
			}
		}
		Timber.i("Taking %d songs from shuffle play buffer. %d remaining.", result.size(), buffer.size());
		return result;
	}

	private void refill()
	{
		if (!isEnabled) return;

		// Check if active server has changed.
		clearBufferIfNecessary();

		if (buffer.size() > REFILL_THRESHOLD || (!Util.isNetworkConnected() && !ActiveServerProvider.Companion.isOffline()))
		{
			return;
		}

		try
		{
			MusicService service = MusicServiceFactory.getMusicService();
			int n = CAPACITY - buffer.size();
			MusicDirectory songs = service.getRandomSongs(n);

			synchronized (buffer)
			{
				buffer.addAll(songs.getChildren());
				Timber.i("Refilled shuffle play buffer with %d songs.", songs.getChildren().size());
			}
		}
		catch (Exception x)
		{
			Timber.w(x, "Failed to refill shuffle play buffer.");
		}
	}

	private void clearBufferIfNecessary()
	{
		synchronized (buffer)
		{
			if (currentServer != ActiveServerProvider.Companion.getActiveServerId())
			{
				currentServer = ActiveServerProvider.Companion.getActiveServerId();
				buffer.clear();
			}
		}
	}
}