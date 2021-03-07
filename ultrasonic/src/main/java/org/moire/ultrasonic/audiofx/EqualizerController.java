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

import android.content.Context;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import timber.log.Timber;

import org.moire.ultrasonic.util.FileUtil;

import java.io.Serializable;

/**
 * Backward-compatible wrapper for {@link Equalizer}, which is API Level 9.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class EqualizerController
{
	private static Boolean available = null;
	private static final MutableLiveData<EqualizerController> instance = new MutableLiveData<>();

	private Context context;
	public Equalizer equalizer;
	private int audioSessionId;

	/**
	 * Retrieves the EqualizerController as LiveData
	 */
	public static LiveData<EqualizerController> get()
	{
		return instance;
	}

	/**
	 * Initializes the EqualizerController instance with a MediaPlayer
	 */
	public static void create(Context context, MediaPlayer mediaPlayer)
	{
		if (mediaPlayer == null) return;
		if (!isAvailable()) return;

		EqualizerController controller = new EqualizerController();
		controller.context = context;

		try
		{
			controller.audioSessionId = mediaPlayer.getAudioSessionId();
			controller.equalizer = new Equalizer(0, controller.audioSessionId);
			controller.loadSettings();

			instance.postValue(controller);
		}
		catch (Throwable x)
		{
			Timber.w(x, "Failed to create equalizer.");
		}
	}

	/**
	 * Releases the EqualizerController instance when the underlying MediaPlayer is no longer available
	 */
	public static void release()
	{
		EqualizerController controller = instance.getValue();
		if (controller == null) return;

		controller.equalizer.release();
		instance.postValue(null);
	}

	/**
	 * Checks if the {@link Equalizer} class is available.
	 */
	private static boolean isAvailable()
	{
		if (available != null) return available;
		try
		{
			Class.forName("android.media.audiofx.Equalizer");
			available = true;
		}
		catch (Exception ex)
		{
			Timber.i(ex, "CheckAvailable received an exception getting class for the Equalizer");
			available = false;
		}
		return available;
	}

	public void saveSettings()
	{
		if (!available) return;
		try
		{
			FileUtil.serialize(context, new EqualizerSettings(equalizer), "equalizer.dat");
		}
		catch (Throwable x)
		{
			Timber.w(x, "Failed to save equalizer settings.");
		}
	}

	public void loadSettings()
	{
		if (!available) return;
		try
		{
			EqualizerSettings settings = FileUtil.deserialize(context, "equalizer.dat");

			if (settings != null)
			{
				settings.apply(equalizer);
			}
		}
		catch (Throwable x)
		{
			Timber.w(x, "Failed to load equalizer settings.");
		}
	}

	private static class EqualizerSettings implements Serializable
	{
		private static final long serialVersionUID = 626565082425206061L;
		private final short[] bandLevels;
		private short preset;
		private final boolean enabled;

		public EqualizerSettings(Equalizer equalizer)
		{
			enabled = equalizer.getEnabled();
			bandLevels = new short[equalizer.getNumberOfBands()];

			for (short i = 0; i < equalizer.getNumberOfBands(); i++)
			{
				bandLevels[i] = equalizer.getBandLevel(i);
			}

			try
			{
				preset = equalizer.getCurrentPreset();
			}
			catch (Exception x)
			{
				preset = -1;
			}
		}

		public void apply(Equalizer equalizer)
		{
			for (short i = 0; i < bandLevels.length; i++)
			{
				equalizer.setBandLevel(i, bandLevels[i]);
			}

			if (preset >= 0 && preset < equalizer.getNumberOfPresets())
			{
				equalizer.usePreset(preset);
			}

			equalizer.setEnabled(enabled);
		}
	}
}
