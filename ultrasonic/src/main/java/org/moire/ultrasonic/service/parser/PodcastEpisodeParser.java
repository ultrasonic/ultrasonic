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
package org.moire.ultrasonic.service.parser;

import android.content.Context;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PodcastEpisode;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.util.ProgressListener;
import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author Sindre Mehus
 */
public class PodcastEpisodeParser extends AbstractParser
{

	public PodcastEpisodeParser(Context context)
	{
		super(context);
	}

	public MusicDirectory parse(Reader reader, ProgressListener progressListener) throws Exception
	{

		MusicDirectory musicDirectory = new MusicDirectory();
        SortedMap<Date,MusicDirectory.Entry> sortedEntries = new TreeMap<Date, MusicDirectory.Entry>();

        Locale currentLocale = getContext().getResources().getConfiguration().locale;

        DateFormat shortDateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT, currentLocale);
        DateFormat parseDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

		updateProgress(progressListener, R.string.parser_reading);
		init(reader);

		int eventType;
		do
		{
			eventType = nextParseEvent();
			if (eventType == XmlPullParser.START_TAG)
			{
				String tag = getElementName();
				if ("episode".equals(tag))
				{
                    String status = get("status");
                    if (!"skipped".equals(status) && !"error".equals(status)) {
                        MusicDirectory.Entry entry = new MusicDirectory.Entry();
                        String streamId = get("streamId");
                        entry.setId(streamId);
                        entry.setIsDirectory(Boolean.parseBoolean(get("isDir")));
                        entry.setIsVideo(Boolean.parseBoolean(get("isVideo")));
                        entry.setType(get("type"));
                        entry.setPath(get("path"));
                        entry.setSuffix(get("suffix"));
                        String size = get("size");
                        if (size != null) {
                            entry.setSize(Long.parseLong(size));
                        }
                        entry.setCoverArt(get("coverArt"));
                        entry.setAlbum(get("album"));
                        entry.setTitle(get("title"));
                        entry.setAlbumId(get("albumId"));
                        entry.setArtist(get("artist"));
                        entry.setArtistId(get("artistId"));
                        String bitRate = get("bitRate");
                        if (bitRate != null) {
                            entry.setBitRate(Integer.parseInt(get("bitRate")));
                        }
                        entry.setContentType(get("contentType"));
                        String duration = get("duration");
                        if (duration != null) {
                            entry.setDuration(Long.parseLong(duration));
                        }
                        entry.setGenre(get("genre"));
                        entry.setParent(get("parent"));
                        entry.setCreated("created");


                        String publishDate = get("publishDate");
                        if (publishDate != null) {
                            try {
                                Date publishDateDate = parseDateFormat.parse(publishDate);
                                entry.setArtist(shortDateFormat.format(publishDateDate));
                                sortedEntries.put(publishDateDate, entry);
                            } catch (Exception e) {
                                // nothing to do
                            }
                        }
                    }
				}
				else if ("error".equals(tag))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		updateProgress(progressListener, R.string.parser_reading_done);

        for (Date pubDate : sortedEntries.keySet()) {
            musicDirectory.addFirst(sortedEntries.get(pubDate));
        }
		return musicDirectory;
	}
}

