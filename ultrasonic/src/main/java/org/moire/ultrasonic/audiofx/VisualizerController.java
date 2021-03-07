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

 Copyright 2011 (C) Sindre Mehus
 */
package org.moire.ultrasonic.audiofx;

import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import timber.log.Timber;

/**
 * Backward-compatible wrapper for {@link Visualizer}, which is API Level 9.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class VisualizerController
{
	private static final int PREFERRED_CAPTURE_SIZE = 128; // Must be a power of two.
	private static Boolean available = null;
	private static final MutableLiveData<VisualizerController> instance = new MutableLiveData<>();

	public Visualizer visualizer;
	private int audioSessionId;

	/**
	 * Retrieves the VisualizerController as LiveData
	 */
	public static LiveData<VisualizerController> get()
	{
		return instance;
	}

	/**
	 * Initializes the VisualizerController instance with a MediaPlayer
	 */
	public static void create(MediaPlayer mediaPlayer)
	{
		if (mediaPlayer == null) return;
		if (!isAvailable()) return;

		VisualizerController controller = new VisualizerController();

		try
		{
			controller.audioSessionId = mediaPlayer.getAudioSessionId();
			controller.visualizer = new Visualizer(controller.audioSessionId);

			int[] captureSizeRange = Visualizer.getCaptureSizeRange();
			int captureSize = Math.max(PREFERRED_CAPTURE_SIZE, captureSizeRange[0]);
			captureSize = Math.min(captureSize, captureSizeRange[1]);
			controller.visualizer.setCaptureSize(captureSize);

			instance.postValue(controller);
		}
		catch (Throwable x)
		{
			Timber.w(x, "Failed to create visualizer.");
		}
	}

	/**
	 * Releases the VisualizerController instance when the underlying MediaPlayer is no longer available
	 */
	public static void release()
	{
		VisualizerController controller = instance.getValue();
		if (controller == null) return;

		controller.visualizer.release();
		instance.postValue(null);
	}

	/**
	 * Checks if the {@link Visualizer} class is available.
	 */
	private static boolean isAvailable()
	{
		if (available != null) return available;
		try
		{
			Class.forName("android.media.audiofx.Visualizer");
			available = true;
		}
		catch (Exception ex)
		{
			Timber.i(ex, "CheckAvailable received an exception getting class for the Visualizer");
			available = false;
		}
		return available;
	}
}