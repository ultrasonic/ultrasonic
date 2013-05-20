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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous loading of images, with caching.
 * <p/>
 * There should normally be only one instance of this class.
 *
 * @author Sindre Mehus
 */
public class ImageLoader implements Runnable {

    private static final String TAG = ImageLoader.class.getSimpleName();
    private static final int CONCURRENCY = 5;

    private final LRUCache<String, Bitmap> cache = new LRUCache<String, Bitmap>(100);
    private final BlockingQueue<Task> queue;
    private final int imageSizeDefault;
    private final int imageSizeLarge;
    private Bitmap largeUnknownImage;
	private Context context;
	
    public ImageLoader(Context context) {
    	this.context = context;
    	queue = new LinkedBlockingQueue<Task>(500);

        // Determine the density-dependent image sizes.
        imageSizeDefault = context.getResources().getDrawable(R.drawable.unknown_album).getIntrinsicHeight();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        imageSizeLarge = (int) Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));

        for (int i = 0; i < CONCURRENCY; i++) {
            new Thread(this, "ImageLoader").start();
        }

        createLargeUnknownImage(context);
    }

    private void createLargeUnknownImage(Context context) {
        BitmapDrawable drawable = (BitmapDrawable) context.getResources().getDrawable(R.drawable.unknown_album_large);
        Log.i(TAG, "createLargeUnknownImage");
        largeUnknownImage = Util.scaleBitmap(drawable.getBitmap(), imageSizeLarge);
    }

    public void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size, boolean crossfade, boolean highQuality) {
    	if (entry == null) {
            setUnknownImage(view, large);
            return;
    	}
    	
    	String coverArt = entry.getCoverArt();
    	
        if (coverArt == null) {
            setUnknownImage(view, large);
            return;
        }
        
        if (size <= 0) {
        	size = large ? imageSizeLarge : imageSizeDefault;
        }
        
        Bitmap bitmap = cache.get(getKey(coverArt, size));
        
        if (bitmap != null) {
            setImageBitmap(view, bitmap, false);
            return;
        }

        if (!large) {
            setUnknownImage(view, large);
        }
        
        queue.offer(new Task(view, entry, size, large, large, crossfade, highQuality));
    }
    
    private String getKey(String coverArtId, int size) {
        return coverArtId + size;
    }

    private void setImageBitmap(View view, Bitmap bitmap, boolean crossfade) {
       if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            if (crossfade) {

                Drawable existingDrawable = imageView.getDrawable();
                Drawable newDrawable = Util.createDrawableFromBitmap(this.context, bitmap);
                
                if (existingDrawable == null) {
                    Bitmap emptyImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    existingDrawable = new BitmapDrawable(emptyImage);
                }

                Drawable[] layers = new Drawable[]{existingDrawable, newDrawable};

                TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
                imageView.setImageDrawable(transitionDrawable);
                transitionDrawable.startTransition(250);
            } else {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    private void setUnknownImage(View view, boolean large) {
        if (large) {
            setImageBitmap(view, largeUnknownImage, false);
        } else {
            if (view instanceof TextView) {
                ((TextView) view).setCompoundDrawablesWithIntrinsicBounds(R.drawable.unknown_album, 0, 0, 0);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(R.drawable.unknown_album);
            }
        }
    }

    public void clear() {
        queue.clear();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Task task = queue.take();
                task.execute();
            } catch (Throwable x) {
                Log.e(TAG, "Unexpected exception in ImageLoader.", x);
            }
        }
    }

    private class Task {
        private final View view;
        private final MusicDirectory.Entry entry;
        private final Handler handler;
        private final int size;
        private final boolean saveToFile;
        private final boolean crossfade;
        private final boolean highQuality;

        public Task(View view, MusicDirectory.Entry entry, int size, boolean reflection, boolean saveToFile, boolean crossfade, boolean highQuality) {
            this.view = view;
            this.entry = entry;
            this.size = size;
            this.saveToFile = saveToFile;
            this.crossfade = crossfade;
            this.highQuality = highQuality;
            handler = new Handler();
        }
        
        public void execute() {
            try {
                MusicService musicService = MusicServiceFactory.getMusicService(view.getContext());
                final Bitmap bitmap = musicService.getCoverArt(view.getContext(), entry, size, saveToFile, highQuality, null);

                cache.put(getKey(entry.getCoverArt(), size), bitmap);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setImageBitmap(view, bitmap, crossfade);
                    }
                });
            } catch (Throwable x) {
                Log.e(TAG, "Failed to download album art.", x);
            }
        }
    }
}
