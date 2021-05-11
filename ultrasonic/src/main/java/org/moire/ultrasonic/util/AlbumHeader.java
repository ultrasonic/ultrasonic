package org.moire.ultrasonic.util;

import android.content.Context;

import org.moire.ultrasonic.domain.MusicDirectory;

import java.util.HashSet;
import java.util.Set;

public class AlbumHeader
{
	private boolean isAllVideo;
	private long totalDuration;
	private Set<String> artists;
	private Set<String> grandParents;
	private Set<String> genres;
	private Set<Integer> years;

	public boolean getIsAllVideo()
	{
		return isAllVideo;
	}

	public long getTotalDuration()
	{
		return totalDuration;
	}

	public Set<String> getArtists()
	{
		return artists;
	}

	public Set<String> getGrandParents()
	{
		return this.grandParents;
	}

	public Set<String> getGenres()
	{
		return this.genres;
	}

	public Set<Integer> getYears()
	{
		return this.years;
	}

	public AlbumHeader()
	{
		this.artists = new HashSet<String>();
		this.grandParents = new HashSet<String>();
		this.genres = new HashSet<String>();
		this.years = new HashSet<Integer>();

		this.isAllVideo = true;
		this.totalDuration = 0;
	}

	public static AlbumHeader processEntries(Context context, Iterable<MusicDirectory.Entry> entries)
	{
		AlbumHeader albumHeader = new AlbumHeader();

		for (MusicDirectory.Entry entry : entries)
		{
			if (!entry.isVideo())
			{
				albumHeader.isAllVideo = false;
			}

			if (!entry.isDirectory())
			{
				if (Util.shouldUseFolderForArtistName())
				{
					albumHeader.processGrandParents(entry);
				}

				if (entry.getArtist() != null)
				{
					Integer duration = entry.getDuration();

					if (duration != null)
					{
						albumHeader.totalDuration += duration;
					}

					albumHeader.artists.add(entry.getArtist());
				}

				if (entry.getGenre() != null)
				{
					albumHeader.genres.add(entry.getGenre());
				}

				if (entry.getYear() != null)
				{
					albumHeader.years.add(entry.getYear());
				}
			}
		}

		return albumHeader;
	}

	private void processGrandParents(MusicDirectory.Entry entry)
	{
		String grandParent = Util.getGrandparent(entry.getPath());

		if (grandParent != null)
		{
			this.grandParents.add(grandParent);
		}
	}
}
