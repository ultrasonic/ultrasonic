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
package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;

/**
 * @author Sindre Mehus
 */
public class MusicDirectoryParser extends MusicDirectoryEntryParser
{

	private static final String TAG = MusicDirectoryParser.class.getSimpleName();

	public MusicDirectoryParser(Context context)
	{
		super(context);
	}

	public MusicDirectory parse(String artist, Reader reader, ProgressListener progressListener, boolean isAlbum) throws Exception
	{

		long t0 = System.currentTimeMillis();
		updateProgress(progressListener, R.string.parser_reading);
		init(reader);

		MusicDirectory dir = new MusicDirectory();
		int eventType;
		do
		{
			eventType = nextParseEvent();
			if (eventType == XmlPullParser.START_TAG)
			{
				String name = getElementName();

				if ("child".equals(name) || "song".equals(name) || "video".equals(name))
				{
					dir.addChild(parseEntry(artist, false, 0));
				}
				else if ("album".equals(name) && !isAlbum)
				{
					dir.addChild(parseEntry(artist, true, 0));
				}
				else if ("directory".equals(name) || "artist".equals(name))
				{
					dir.setName(get("name"));
				}
				else if ("error".equals(name))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		updateProgress(progressListener, R.string.parser_reading_done);

		long t1 = System.currentTimeMillis();
		Log.d(TAG, "Got music directory in " + (t1 - t0) + "ms.");

		return dir;
	}
}