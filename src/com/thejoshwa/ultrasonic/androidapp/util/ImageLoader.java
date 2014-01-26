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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous loading of images, with caching.
 * <p/>
 * There should normally be only one instance of this class.
 *
 * @author Sindre Mehus
 */
public class ImageLoader implements Runnable
{
	private static final String TAG = ImageLoader.class.getSimpleName();

	private final LRUCache<String, Bitmap> cache = new LRUCache<String, Bitmap>(150);
	private final BlockingQueue<Task> queue;
	private int imageSizeDefault;
	private final int imageSizeLarge;
	private Bitmap largeUnknownImage;
	private Context context;
	private Collection<Thread> threads;
	private AtomicBoolean running = new AtomicBoolean();
	private int concurrency;

	public ImageLoader(Context context, int concurrency)
	{
		this.context = context;
		this.concurrency = concurrency;
		queue = new LinkedBlockingQueue<Task>(1000);

		Resources resources = context.getResources();
		Drawable drawable = resources.getDrawable(R.drawable.unknown_album);

		// Determine the density-dependent image sizes.
		if (drawable != null)
		{
			imageSizeDefault = drawable.getIntrinsicHeight();
		}

		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		imageSizeLarge = Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));

		createLargeUnknownImage(context);
	}

	public synchronized boolean isRunning()
	{
		return running.get() && !threads.isEmpty();
	}

	public void setConcurrency(int concurrency)
	{
		this.concurrency = concurrency;
	}

	public void startImageLoader()
	{
		running.set(true);

		threads = Collections.synchronizedCollection(new ArrayList<Thread>(this.concurrency));

		for (int i = 0; i < this.concurrency; i++)
		{
			Thread thread = new Thread(this, String.format("ImageLoader [%d]", i));
			threads.add(thread);
			thread.start();
		}
	}

	public synchronized void stopImageLoader()
	{
		clear();

		for (Thread thread : threads)
		{
			thread.interrupt();
		}

		running.set(false);
		threads.clear();
	}

	private void createLargeUnknownImage(Context context)
	{
		BitmapDrawable drawable = (BitmapDrawable) context.getResources().getDrawable(R.drawable.unknown_album_large);
		Log.i(TAG, "createLargeUnknownImage");

		if (drawable != null)
		{
			largeUnknownImage = Util.scaleBitmap(drawable.getBitmap(), imageSizeLarge);
		}
	}

	public void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size, boolean crossFade, boolean highQuality)
	{
		if (entry == null)
		{
			setUnknownImage(view, large);
			return;
		}

		String coverArt = entry.getCoverArt();

		if (coverArt == null)
		{
			setUnknownImage(view, large);
			return;
		}

		if (size <= 0)
		{
			size = large ? imageSizeLarge : imageSizeDefault;
		}

		Bitmap bitmap = cache.get(getKey(coverArt, size));

		if (bitmap != null)
		{
			setImageBitmap(view, entry, bitmap, crossFade);
			return;
		}

		setUnknownImage(view, large);

		queue.offer(new Task(view, entry, size, large, crossFade, highQuality));
	}

	private static String getKey(String coverArtId, int size)
	{
		return String.format("%s:%d", coverArtId, size);
	}

	public Bitmap getImageBitmap(MusicDirectory.Entry entry, boolean large, int size)
	{
		if (entry == null)
		{
			return null;
		}

		String coverArt = entry.getCoverArt();

		if (coverArt == null)
		{
			return null;
		}

		if (size <= 0)
		{
			size = large ? imageSizeLarge : imageSizeDefault;
		}

		Bitmap bitmap = cache.get(getKey(coverArt, size));

		if (bitmap != null)
		{
			Bitmap.Config config = bitmap.getConfig();
			return bitmap.copy(config, false);
		}

		return null;
	}

	private void setImageBitmap(View view, MusicDirectory.Entry entry, Bitmap bitmap, boolean crossFade)
	{
		if (view instanceof ImageView)
		{
			ImageView imageView = (ImageView) view;

			MusicDirectory.Entry tagEntry = (MusicDirectory.Entry) view.getTag();

			// Only apply image to the view if the view is intended for this entry
			if (entry != null && tagEntry != null && !entry.equals(tagEntry))
			{
				Log.i(TAG, "View is no longer valid, not setting ImageBitmap");
				return;
			}

			if (crossFade)
			{
				Drawable existingDrawable = imageView.getDrawable();
				Drawable newDrawable = Util.createDrawableFromBitmap(this.context, bitmap);

				if (existingDrawable == null)
				{
					Bitmap emptyImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
					existingDrawable = new BitmapDrawable(emptyImage);
				}

				Drawable[] layers = new Drawable[]{existingDrawable, newDrawable};

				TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
				imageView.setImageDrawable(transitionDrawable);
				transitionDrawable.startTransition(250);
			}
			else
			{
				imageView.setImageBitmap(bitmap);
			}
		}
	}

	public void setUnknownImage(View view, boolean large)
	{
		if (large)
		{
			setImageBitmap(view, null, largeUnknownImage, false);
		}
		else
		{
			if (view instanceof TextView)
			{
				((TextView) view).setCompoundDrawablesWithIntrinsicBounds(R.drawable.unknown_album, 0, 0, 0);
			}
			else if (view instanceof ImageView)
			{
				((ImageView) view).setImageResource(R.drawable.unknown_album);
			}
		}
	}

	public void addImageToCache(Bitmap bitmap, MusicDirectory.Entry entry, int size)
	{
		cache.put(getKey(entry.getCoverArt(), size), bitmap);
	}

	public void clear()
	{
		queue.clear();
	}

	@Override
	public void run()
	{
		while (running.get())
		{
			try
			{
				Task task = queue.take();
				task.execute();
			}
			catch (InterruptedException ignored)
			{
				running.set(false);
				break;
			}
			catch (Throwable x)
			{
				Log.e(TAG, "Unexpected exception in ImageLoader.", x);
			}
		}
	}

	private class Task
	{
		private final View view;
		private final MusicDirectory.Entry entry;
		private final Handler handler;
		private final int size;
		private final boolean saveToFile;
		private final boolean crossFade;
		private final boolean highQuality;

		public Task(View view, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean crossFade, boolean highQuality)
		{
			this.view = view;
			this.entry = entry;
			this.size = size;
			this.saveToFile = saveToFile;
			this.crossFade = crossFade;
			this.highQuality = highQuality;
			handler = new Handler();
		}

		public void execute()
		{
			try
			{
				MusicService musicService = MusicServiceFactory.getMusicService(view.getContext());
				final Bitmap bitmap = musicService.getCoverArt(view.getContext(), entry, size, saveToFile, highQuality, null);

				addImageToCache(bitmap, entry, size);

				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						setImageBitmap(view, entry, bitmap, crossFade);
					}
				});
			}
			catch (Throwable x)
			{
				Log.e(TAG, "Failed to download album art.", x);
			}
		}
	}
}
