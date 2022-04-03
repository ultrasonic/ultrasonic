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
package org.moire.ultrasonic.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.view.View;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.service.MediaPlayerController;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * A simple class that draws waveform data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture}
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class VisualizerView extends View
{
	private static final int PREFERRED_CAPTURE_RATE_MILLIHERTZ = 20000;

	private final Paint paint = new Paint();
	private Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);

	private byte[] data;
	private float[] points;
	private boolean active;
	private Visualizer visualizer;

	public VisualizerView(final Context context)
	{
		super(context);

		paint.setStrokeWidth(2f);
		paint.setAntiAlias(true);
		paint.setColor(Color.rgb(0, 153, 204));

		VisualizerController.get().observe((LifecycleOwner) context, new Observer<VisualizerController>() {
			@Override
			public void onChanged(VisualizerController controller) {
				if (controller != null) {
					Timber.d("VisualizerController Observer.onChanged received controller");
					visualizer = controller.visualizer;
					setActive(true);
				} else {
					Timber.d("VisualizerController Observer.onChanged has no controller");
					visualizer = null;
					setActive(false);
				}
			}
		});
	}

	public boolean isActive()
	{
		return active;
	}

	public void setActive(boolean value)
	{
		active = value;
		int captureRate = Math.min(PREFERRED_CAPTURE_RATE_MILLIHERTZ, Visualizer.getMaxCaptureRate());
		if (active)
		{
			visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener()
			{
				@Override
				public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate)
				{
					updateVisualizer(waveform);
				}

				@Override
				public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate)
				{
				}
			}, captureRate, true, false);
		}
		else
		{
			if (visualizer != null) visualizer.setDataCaptureListener(null, captureRate, false, false);
		}

		if (visualizer != null) visualizer.setEnabled(active);
		invalidate();
	}

	private void updateVisualizer(byte[] waveform)
	{
		this.data = waveform;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		if (!active)
		{
			return;
		}

		if (mediaPlayerControllerLazy.getValue().getLegacyPlayerState() != PlayerState.STARTED)
		{
			return;
		}

		if (data == null)
		{
			return;
		}

		if (points == null || points.length < data.length * 4)
		{
			points = new float[data.length * 4];
		}

		int w = getWidth();
		int h = getHeight();

		for (int i = 0; i < data.length - 1; i++)
		{
			points[i * 4] = w * i / (data.length - 1);
			points[i * 4 + 1] = h / 2 + ((byte) (data[i] + 128)) * (h / 2) / 128;
			points[i * 4 + 2] = w * (i + 1) / (data.length - 1);
			points[i * 4 + 3] = h / 2 + ((byte) (data[i + 1] + 128)) * (h / 2) / 128;
		}

		canvas.drawLines(points, paint);
	}
}
