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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.text.TextUtils;
import timber.log.Timber;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
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
public class LegacyImageLoader implements Runnable, ImageLoader {
    private final LRUCache<String, Bitmap> cache = new LRUCache<>(150);
    private final BlockingQueue<Task> queue;
    private int imageSizeDefault;
    private final int imageSizeLarge;
    private Bitmap largeUnknownImage;
    private Bitmap unknownAvatarImage;
    private final Context context;
    private Collection<Thread> threads;
    private final AtomicBoolean running = new AtomicBoolean();
    private int concurrency;

    public LegacyImageLoader(
            Context context,
            int concurrency
    ) {
        this.context = context;
        this.concurrency = concurrency;
        queue = new LinkedBlockingQueue<>(1000);

        Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.unknown_album, null);

        // Determine the density-dependent image sizes.
        if (drawable != null) {
            imageSizeDefault = drawable.getIntrinsicHeight();
        }

        imageSizeLarge = Util.getMaxDisplayMetric(context);
        createLargeUnknownImage(context);
        createUnknownAvatarImage(context);
    }

    @Override
    public synchronized boolean isRunning() {
        return running.get() && !threads.isEmpty();
    }

    @Override
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    @Override
    public void startImageLoader() {
        running.set(true);

        threads = Collections.synchronizedCollection(new ArrayList<Thread>(this.concurrency));

        for (int i = 0; i < this.concurrency; i++) {
            Thread thread = new Thread(this, String.format(Locale.US, "ImageLoader_%d", i));
            threads.add(thread);
            thread.start();
        }
    }

    @Override
    public synchronized void stopImageLoader() {
        clear();

        for (Thread thread : threads) {
            thread.interrupt();
        }

        running.set(false);
        threads.clear();
    }

    private void createLargeUnknownImage(Context context) {
        Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.unknown_album, null);
        Timber.i("createLargeUnknownImage");

        if (drawable != null) {
            largeUnknownImage = Util.createBitmapFromDrawable(drawable);
        }
    }

    private void createUnknownAvatarImage(Context context) {
        Drawable contact = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_contact_picture, null);
        unknownAvatarImage = Util.createBitmapFromDrawable(contact);
    }

    @Override
    public void loadAvatarImage(
            View view,
            String username,
            boolean large,
            int size,
            boolean crossFade,
            boolean highQuality
    ) {
        view.invalidate();

        if (username == null) {
            setUnknownAvatarImage(view);
            return;
        }

        if (size <= 0) {
            size = large ? imageSizeLarge : imageSizeDefault;
        }

        Bitmap bitmap = cache.get(getKey(username, size));

        if (bitmap != null) {
            setAvatarImageBitmap(view, username, bitmap, crossFade);
            return;
        }

        setUnknownAvatarImage(view);

        queue.offer(new Task(view, username, size, large, crossFade, highQuality));
    }

    @Override
    public void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size,
        boolean crossFade, boolean highQuality) {
        loadImage(view, entry, large, size, crossFade, highQuality, -1);
    }

    public void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size,
        boolean crossFade, boolean highQuality, int defaultResourceId) {
        view.invalidate();

        if (entry == null) {
            setUnknownImage(view, large, defaultResourceId);
            return;
        }

        String coverArt = entry.getCoverArt();

        if (TextUtils.isEmpty(coverArt)) {
            setUnknownImage(view, large, defaultResourceId);
            return;
        }

        if (size <= 0) {
            size = large ? imageSizeLarge : imageSizeDefault;
        }

        Bitmap bitmap = cache.get(getKey(coverArt, size));

        if (bitmap != null) {
            setImageBitmap(view, entry, bitmap, crossFade);
            return;
        }

        setUnknownImage(view, large, defaultResourceId);

        queue.offer(new Task(view, entry, size, large, crossFade, highQuality));
    }

    public void cancel(String coverArt) {
        for (Object taskObject : queue.toArray()) {
            Task task = (Task)taskObject;
            if ((task.entry.getCoverArt() != null) && (coverArt.compareTo(task.entry.getCoverArt()) == 0)) {
                queue.remove(taskObject);
                break;
            }
        }
    }

    private static String getKey(String coverArtId, int size) {
        return String.format(Locale.US, "%s:%d", coverArtId, size);
    }

    @Override
    public Bitmap getImageBitmap(String username, int size) {
        Bitmap bitmap = cache.get(getKey(username, size));

        if (bitmap != null && !bitmap.isRecycled()) {
            Bitmap.Config config = bitmap.getConfig();
            return bitmap.copy(config, false);
        }

        return null;
    }

    @Override
    public Bitmap getImageBitmap(MusicDirectory.Entry entry, boolean large, int size) {
        if (entry == null) {
            return null;
        }

        String coverArt = entry.getCoverArt();

        if (TextUtils.isEmpty(coverArt)) {
            return null;
        }

        if (size <= 0) {
            size = large ? imageSizeLarge : imageSizeDefault;
        }

        Bitmap bitmap = cache.get(getKey(coverArt, size));

        if (bitmap != null && !bitmap.isRecycled()) {
            Bitmap.Config config = bitmap.getConfig();
            return bitmap.copy(config, false);
        }

        return null;
    }

    private void setImageBitmap(
            View view,
            MusicDirectory.Entry entry,
            Bitmap bitmap,
            boolean crossFade
    ) {
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;

            MusicDirectory.Entry tagEntry = (MusicDirectory.Entry) view.getTag();

            // Only apply image to the view if the view is intended for this entry
            if (entry != null && tagEntry != null && !entry.equals(tagEntry)) {
                Timber.i("View is no longer valid, not setting ImageBitmap");
                return;
            }

            if (crossFade) {
                Drawable existingDrawable = imageView.getDrawable();
                Drawable newDrawable = Util.createDrawableFromBitmap(this.context, bitmap);

                if (existingDrawable == null) {
                    Bitmap emptyImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    existingDrawable = new BitmapDrawable(context.getResources(), emptyImage);
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

    private void setAvatarImageBitmap(
            View view,
            String username,
            Bitmap bitmap,
            boolean crossFade
    ) {
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;

            String tagEntry = (String) view.getTag();

            // Only apply image to the view if the view is intended for this entry
            if (username != null &&
                    tagEntry != null &&
                    !username.equals(tagEntry)) {
                Timber.i("View is no longer valid, not setting ImageBitmap");
                return;
            }

            if (crossFade) {
                Drawable existingDrawable = imageView.getDrawable();
                Drawable newDrawable = Util.createDrawableFromBitmap(this.context, bitmap);

                if (existingDrawable == null) {
                    Bitmap emptyImage = Bitmap.createBitmap(
                            bitmap.getWidth(),
                            bitmap.getHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                    existingDrawable = new BitmapDrawable(context.getResources(), emptyImage);
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

    private void setUnknownAvatarImage(View view) {
        setAvatarImageBitmap(view, null, unknownAvatarImage, false);
    }

    private void setUnknownImage(View view, boolean large, int resId) {
        if (resId == -1) resId = R.drawable.unknown_album;
        if (large) {
            setImageBitmap(view, null, largeUnknownImage, false);
        } else {
            if (view instanceof TextView) {
                ((TextView) view).setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(resId);
            }
        }
    }

    @Override
    public void addImageToCache(Bitmap bitmap, MusicDirectory.Entry entry, int size) {
        cache.put(getKey(entry.getCoverArt(), size), bitmap);
    }

    @Override
    public void addImageToCache(Bitmap bitmap, String username, int size) {
        cache.put(getKey(username, size), bitmap);
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Task task = queue.take();
                task.execute();
            } catch (InterruptedException ignored) {
                running.set(false);
                break;
            } catch (Throwable x) {
                Timber.e(x, "Unexpected exception in ImageLoader.");
            }
        }
    }

    private class Task {
        private final View view;
        private final MusicDirectory.Entry entry;
        private final String username;
        private final Handler handler;
        private final int size;
        private final boolean saveToFile;
        private final boolean crossFade;
        private final boolean highQuality;

        Task(View view, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean crossFade, boolean highQuality) {
            this.view = view;
            this.entry = entry;
            this.username = null;
            this.size = size;
            this.saveToFile = saveToFile;
            this.crossFade = crossFade;
            this.highQuality = highQuality;
            handler = new Handler();
        }

        Task(View view, String username, int size, boolean saveToFile, boolean crossFade, boolean highQuality) {
            this.view = view;
            this.entry = null;
            this.username = username;
            this.size = size;
            this.saveToFile = saveToFile;
            this.crossFade = crossFade;
            this.highQuality = highQuality;
            handler = new Handler();
        }

        public void execute() {
            try {
                MusicService musicService = MusicServiceFactory.getMusicService();
                final boolean isAvatar = this.username != null && this.entry == null;
                final Bitmap bitmap = this.entry != null ?
                    musicService.getCoverArt(entry, size, saveToFile, highQuality) :
                    musicService.getAvatar(username, size, saveToFile, highQuality);

                if (bitmap == null) {
                    Timber.d("Found empty album art.");
                    return;
                }

                if (isAvatar)
                    addImageToCache(bitmap, username, size);
                else
                    addImageToCache(bitmap, entry, size);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isAvatar) {
                            setAvatarImageBitmap(view, username, bitmap, crossFade);
                        } else {
                            setImageBitmap(view, entry, bitmap, crossFade);
                        }
                    }
                });
            } catch (Throwable x) {
                Timber.e(x, "Failed to download album art.");
            }
        }
    }
}
