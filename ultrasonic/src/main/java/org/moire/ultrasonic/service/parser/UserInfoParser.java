package org.moire.ultrasonic.service.parser;

import android.content.Context;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.UserInfo;
import org.moire.ultrasonic.util.ProgressListener;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;

/**
 * @author Joshua Bahnsen
 */
public class UserInfoParser extends AbstractParser
{
	public UserInfoParser(Context context)
	{
		super(context);
	}

	public UserInfo parse(Reader reader, ProgressListener progressListener) throws Exception
	{
		updateProgress(progressListener, R.string.parser_reading);
		init(reader);
		UserInfo result = new UserInfo();
		int eventType;

		do
		{
			eventType = nextParseEvent();

			if (eventType == XmlPullParser.START_TAG)
			{
				String name = getElementName();

				if ("user".equals(name))
				{
					result.setAdminRole(getBoolean("adminRole"));
					result.setCommentRole(getBoolean("commentRole"));
					result.setCoverArtRole(getBoolean("coverArtRole"));
					result.setDownloadRole(getBoolean("downloadRole"));
					result.setEmail(get("email"));
					result.setJukeboxRole(getBoolean("jukeboxRole"));
					result.setPlaylistRole(getBoolean("playlistRole"));
					result.setPodcastRole(getBoolean("podcastRole"));
					result.setScrobblingEnabled(getBoolean("scrobblingEnabled"));
					result.setSettingsRole(getBoolean("settingsRole"));
					result.setShareRole(getBoolean("shareRole"));
					result.setStreamRole(getBoolean("streamRole"));
					result.setUploadRole(getBoolean("uploadRole"));
					result.setUserName(get("username"));
				}
				else if ("error".equals(name))
				{
					handleError();
				}
			}
		} while (eventType != XmlPullParser.END_DOCUMENT);

		validate();
		updateProgress(progressListener, R.string.parser_reading_done);

		return result;
	}
}
