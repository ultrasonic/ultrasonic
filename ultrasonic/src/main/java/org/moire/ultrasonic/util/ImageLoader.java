package org.moire.ultrasonic.util;

import android.graphics.Bitmap;
import android.view.View;
import org.moire.ultrasonic.domain.MusicDirectory;

public interface ImageLoader {
    boolean isRunning();

    void setConcurrency(int concurrency);

    void startImageLoader();

    void stopImageLoader();

    void loadAvatarImage(
            View view,
            String username,
            boolean large,
            int size,
            boolean crossFade,
            boolean highQuality
    );

    void loadImage(
            View view,
            MusicDirectory.Entry entry,
            boolean large,
            int size,
            boolean crossFade,
            boolean highQuality
    );

    void cancel(String coverArt);

    Bitmap getImageBitmap(String username, int size);

    Bitmap getImageBitmap(MusicDirectory.Entry entry, boolean large, int size);

    void addImageToCache(Bitmap bitmap, MusicDirectory.Entry entry, int size);

    void addImageToCache(Bitmap bitmap, String username, int size);

    void clear();
}
