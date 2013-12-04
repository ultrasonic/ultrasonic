package com.thejoshwa.ultrasonic.androidapp.util;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;

import java.util.Comparator;

public class EntryByDiscAndTrackComparator implements Comparator<MusicDirectory.Entry>
{
	@Override
	public int compare(MusicDirectory.Entry x, MusicDirectory.Entry y)
	{
		Integer discX = x.getDiscNumber();
		Integer discY = y.getDiscNumber();
		Integer trackX = x.getTrack();
		Integer trackY = y.getTrack();
		//		String parentX = x.getParent();
		//		String parentY = y.getParent();
		String albumX = x.getAlbum();
		String albumY = y.getAlbum();

		//		int parentComparison = compare(parentX, parentY);
		//
		//		if (parentComparison != 0) {
		//			return parentComparison;
		//		}

		int albumComparison = compare(albumX, albumY);

		if (albumComparison != 0)
		{
			return albumComparison;
		}

		int discComparison = compare(discX == null ? 0 : discX, discY == null ? 0 : discY);

		if (discComparison != 0)
		{
			return discComparison;
		}

		return compare(trackX == null ? 0 : trackX, trackY == null ? 0 : trackY);
	}

	private int compare(long a, long b)
	{
		return a < b ? -1 : a > b ? 1 : 0;
	}

	private int compare(String a, String b)
	{
		if (a == null && b == null)
		{
			return 0;
		}

		if (a == null && b != null)
		{
			return -1;
		}

		if (a != null && b == null)
		{
			return 1;
		}

		return a.compareTo(b);
	}
}