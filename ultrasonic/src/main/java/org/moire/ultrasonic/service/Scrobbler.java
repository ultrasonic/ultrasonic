package org.moire.ultrasonic.service;

import android.content.Context;
import timber.log.Timber;

import org.moire.ultrasonic.data.ActiveServerProvider;

/**
 * Scrobbles played songs to Last.fm.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class Scrobbler
{
	private String lastSubmission;
	private String lastNowPlaying;

	public void scrobble(final Context context, final DownloadFile song, final boolean submission)
	{
		if (song == null || !ActiveServerProvider.Companion.isScrobblingEnabled()) return;

		final String id = song.getSong().getId();
		if (id == null) return;

		// Avoid duplicate registrations.
		if (submission && id.equals(lastSubmission)) return;

		if (!submission && id.equals(lastNowPlaying)) return;

		if (submission)	lastSubmission = id;
		else lastNowPlaying = id;

		new Thread(String.format("Scrobble %s", song))
		{
			@Override
			public void run()
			{
				MusicService service = MusicServiceFactory.getMusicService();
				try
				{
					service.scrobble(id, submission, context);
					Timber.i("Scrobbled '%s' for %s", submission ? "submission" : "now playing", song);
				}
				catch (Exception x)
				{
					Timber.i(x, "Failed to scrobble'%s' for %s", submission ? "submission" : "now playing", song);
				}
			}
		}.start();
	}
}
