package org.moire.ultrasonic.util;

import android.view.View;

import org.moire.ultrasonic.domain.MusicDirectory;

public interface ImageLoader {
    void loadAvatarImage(View view, String username, boolean large, int size, boolean crossFade,
            boolean highQuality);

    void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size,
            boolean crossFade, boolean highQuality);

    void loadImage(View view, MusicDirectory.Entry entry, boolean large, int size,
                   boolean crossFade, boolean highQuality, int defaultResourceId);

}
