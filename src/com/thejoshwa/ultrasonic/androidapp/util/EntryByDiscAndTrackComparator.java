package com.thejoshwa.ultrasonic.androidapp.util;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;

import java.util.Comparator;

public class EntryByDiscAndTrackComparator implements Comparator<MusicDirectory.Entry> {
    @Override
    public int compare(MusicDirectory.Entry x, MusicDirectory.Entry y) {
        Integer discX = x.getDiscNumber();
        Integer discY = y.getDiscNumber();
        Integer trackX = x.getTrack();
        Integer trackY = y.getTrack();

        int startComparison = compare(discX == null ? 0 : discX, discY == null ? 0 : discY);
        return startComparison != 0 ? startComparison : compare(trackX == null ? 0 : trackX, trackY == null ? 0 : trackY);
    }

    private int compare(long a, long b) {
        return a < b ? -1
                : a > b ? 1
                : 0;
    }
}