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
package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class ShufflePlayBuffer
{

	private static final String TAG = ShufflePlayBuffer.class.getSimpleName();
	private static final int CAPACITY = 50;
	private static final int REFILL_THRESHOLD = 40;

	private final ScheduledExecutorService executorService;
	private final List<MusicDirectory.Entry> buffer = new ArrayList<MusicDirectory.Entry>();
	private final Context context;
	private int currentServer;

	public ShufflePlayBuffer(Context context)
	{
		this.context = context;
		executorService = Executors.newSingleThreadScheduledExecutor();
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				refill();
			}
		};
		executorService.scheduleWithFixedDelay(runnable, 1, 10, TimeUnit.SECONDS);
	}

	public List<MusicDirectory.Entry> get(int size)
	{
		clearBufferIfNecessary();

		List<MusicDirectory.Entry> result = new ArrayList<MusicDirectory.Entry>(size);
		synchronized (buffer)
		{
			while (!buffer.isEmpty() && result.size() < size)
			{
				result.add(buffer.remove(buffer.size() - 1));
			}
		}
		Log.i(TAG, String.format("Taking %d songs from shuffle play buffer. %d remaining.", result.size(), buffer.size()));
		return result;
	}

	public void shutdown()
	{
		executorService.shutdown();
	}

	private void refill()
	{

		// Check if active server has changed.
		clearBufferIfNecessary();

		if (buffer.size() > REFILL_THRESHOLD || (!Util.isNetworkConnected(context) && !Util.isOffline(context)))
		{
			return;
		}

		try
		{
			MusicService service = MusicServiceFactory.getMusicService(context);
			int n = CAPACITY - buffer.size();
			MusicDirectory songs = service.getRandomSongs(n, context, null);

			synchronized (buffer)
			{
				buffer.addAll(songs.getChildren());
				Log.i(TAG, String.format("Refilled shuffle play buffer with %d songs.", songs.getChildren().size()));
			}
		}
		catch (Exception x)
		{
			Log.w(TAG, "Failed to refill shuffle play buffer.", x);
		}
	}

	private void clearBufferIfNecessary()
	{
		synchronized (buffer)
		{
			if (currentServer != Util.getActiveServer(context))
			{
				currentServer = Util.getActiveServer(context);
				buffer.clear();
			}
		}
	}
}