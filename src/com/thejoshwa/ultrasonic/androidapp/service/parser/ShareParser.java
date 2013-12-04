package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Joshua Bahnsen
 */
public class ShareParser extends MusicDirectoryEntryParser
{

	public ShareParser(Context context)
	{
		super(context);
	}

	public List<Share> parse(Reader reader, ProgressListener progressListener) throws Exception
	{

		updateProgress(progressListener, R.string.parser_reading);
		init(reader);

		List<Share> dir = new ArrayList<Share>();
		Share share = null;
		int eventType;

		do
		{
			eventType = nextParseEvent();

			if (eventType == XmlPullParser.START_TAG)
			{
				String name = getElementName();

				if ("share".equals(name))
				{
					share = new Share();
					share.setCreated(get("created"));
					share.setDescription(get("description"));
					share.setExpires(get("expires"));
					share.setId(get("id"));
					share.setLastVisited(get("lastVisited"));
					share.setUrl(get("url"));
					share.setUsername(get("username"));
					share.setVisitCount(getLong("visitCount"));
				}
				else if ("entry".equals(name))
				{
					if (share != null)
					{
						share.addEntry(parseEntry(null, false, 0));
					}
				}
				else if ("error".equals(name))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		updateProgress(progressListener, R.string.parser_reading_done);

		return dir;
	}
}