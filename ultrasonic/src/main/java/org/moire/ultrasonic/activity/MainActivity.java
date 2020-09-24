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

package org.moire.ultrasonic.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.MergeAdapter;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;

import java.util.Collections;

import kotlin.Lazy;

import static java.util.Arrays.asList;
import static org.koin.android.viewmodel.compat.ViewModelCompat.viewModel;
import static org.koin.java.KoinJavaComponent.inject;

public class MainActivity extends SubsonicTabActivity
{
	private static boolean infoDialogDisplayed;
	private static boolean shouldUseId3;
	private static int lastActiveServer;

	private Lazy<MediaPlayerLifecycleSupport> lifecycleSupport = inject(MediaPlayerLifecycleSupport.class);
	private Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);
	private Lazy<ServerSettingsModel> serverSettingsModel = viewModel(this, ServerSettingsModel.class);

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Determine first run and migrate server settings to DB as early as possible
		boolean showWelcomeScreen = Util.isFirstRun(this);
		boolean areServersMigrated = serverSettingsModel.getValue().migrateFromPreferences();

		// If there are any servers in the DB, do not show the welcome screen
		showWelcomeScreen &= !areServersMigrated;

		if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_EXIT))
		{
			setResult(Constants.RESULT_CLOSE_ALL);

			if (getMediaPlayerController() != null)
			{
				getMediaPlayerController().stopJukeboxService();
			}

			if (getImageLoader() != null)
			{
				getImageLoader().stopImageLoader();
			}

			finish();
			exit();
			return;
		}

		setContentView(R.layout.main);

		loadSettings();

		final View buttons = LayoutInflater.from(this).inflate(R.layout.main_buttons, null);
		final View serverButton = buttons.findViewById(R.id.main_select_server);
		final TextView serverTextView = serverButton.findViewById(R.id.main_select_server_2);
		final View musicTitle = buttons.findViewById(R.id.main_music);
		final View artistsButton = buttons.findViewById(R.id.main_artists_button);
		final View albumsButton = buttons.findViewById(R.id.main_albums_button);
		final View genresButton = buttons.findViewById(R.id.main_genres_button);
		final View videosTitle = buttons.findViewById(R.id.main_videos_title);
		final View songsTitle = buttons.findViewById(R.id.main_songs);
		final View randomSongsButton = buttons.findViewById(R.id.main_songs_button);
		final View songsStarredButton = buttons.findViewById(R.id.main_songs_starred);
		final View albumsTitle = buttons.findViewById(R.id.main_albums);
		final View albumsNewestButton = buttons.findViewById(R.id.main_albums_newest);
		final View albumsRandomButton = buttons.findViewById(R.id.main_albums_random);
		final View albumsHighestButton = buttons.findViewById(R.id.main_albums_highest);
		final View albumsStarredButton = buttons.findViewById(R.id.main_albums_starred);
		final View albumsRecentButton = buttons.findViewById(R.id.main_albums_recent);
		final View albumsFrequentButton = buttons.findViewById(R.id.main_albums_frequent);
		final View albumsAlphaByNameButton = buttons.findViewById(R.id.main_albums_alphaByName);
		final View albumsAlphaByArtistButton = buttons.findViewById(R.id.main_albums_alphaByArtist);
		final View videosButton = buttons.findViewById(R.id.main_videos);

		lastActiveServer = ActiveServerProvider.Companion.getActiveServerId(this);
		String name = activeServerProvider.getValue().getActiveServer().getName();

		serverTextView.setText(name);

		final ListView list = findViewById(R.id.main_list);

		final MergeAdapter adapter = new MergeAdapter();
		adapter.addViews(Collections.singletonList(serverButton), true);

		if (!ActiveServerProvider.Companion.isOffline(this))
		{
			adapter.addView(musicTitle, false);
			adapter.addViews(asList(artistsButton, albumsButton, genresButton), true);
			adapter.addView(songsTitle, false);
			adapter.addViews(asList(randomSongsButton, songsStarredButton), true);
			adapter.addView(albumsTitle, false);

			if (Util.getShouldUseId3Tags(MainActivity.this))
			{
				shouldUseId3 = true;
				adapter.addViews(asList(albumsNewestButton, albumsRecentButton, albumsFrequentButton, albumsRandomButton, albumsStarredButton, albumsAlphaByNameButton, albumsAlphaByArtistButton), true);
			}
			else
			{
				shouldUseId3 = false;
				adapter.addViews(asList(albumsNewestButton, albumsRecentButton, albumsFrequentButton, albumsHighestButton, albumsRandomButton, albumsStarredButton, albumsAlphaByNameButton, albumsAlphaByArtistButton), true);
			}

			adapter.addView(videosTitle, false);
			adapter.addViews(Collections.singletonList(videosButton), true);

            if (Util.isNetworkConnected(this)) {
                new PingTask(this, false).execute();
            }
		}

		list.setAdapter(adapter);

		list.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (view == serverButton)
				{
					showServers();
				}
				else if (view == albumsNewestButton)
				{
					showAlbumList("newest", R.string.main_albums_newest);
				}
				else if (view == albumsRandomButton)
				{
					showAlbumList("random", R.string.main_albums_random);
				}
				else if (view == albumsHighestButton)
				{
					showAlbumList("highest", R.string.main_albums_highest);
				}
				else if (view == albumsRecentButton)
				{
					showAlbumList("recent", R.string.main_albums_recent);
				}
				else if (view == albumsFrequentButton)
				{
					showAlbumList("frequent", R.string.main_albums_frequent);
				}
				else if (view == albumsStarredButton)
				{
					showAlbumList(Constants.STARRED, R.string.main_albums_starred);
				}
				else if (view == albumsAlphaByNameButton)
				{
					showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_alphaByName);
				}
				else if (view == albumsAlphaByArtistButton)
				{
					showAlbumList("alphabeticalByArtist", R.string.main_albums_alphaByArtist);
				}
				else if (view == songsStarredButton)
				{
					showStarredSongs();
				}
				else if (view == artistsButton)
				{
					showArtists();
				}
				else if (view == albumsButton)
				{
					showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_title);
				}
				else if (view == randomSongsButton)
				{
					showRandomSongs();
				}
				else if (view == genresButton)
				{
					showGenres();
				}
				else if (view == videosButton)
				{
					showVideos();
				}
			}
		});

		final View homeMenuItem = findViewById(R.id.menu_home);
		menuDrawer.setActiveView(homeMenuItem);

		setActionBarTitle(R.string.common_appname);
		setTitle(R.string.common_appname);

		// Remember the current theme.
		theme = Util.getTheme(this);

		showInfoDialog(showWelcomeScreen);
	}

	private void loadSettings()
	{
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		final SharedPreferences preferences = Util.getPreferences(this);

		if (!preferences.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION))
		{
			final SharedPreferences.Editor editor = preferences.edit();
			editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, FileUtil.getDefaultMusicDirectory(this).getPath());
			editor.commit();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		boolean shouldRestart = false;

		boolean id3 = Util.getShouldUseId3Tags(MainActivity.this);
		int currentActiveServer = ActiveServerProvider.Companion.getActiveServerId(MainActivity.this);

		if (id3 != shouldUseId3)
		{
			shouldUseId3 = id3;
			shouldRestart = true;
		}

		if (currentActiveServer != lastActiveServer)
		{
			lastActiveServer = currentActiveServer;
			shouldRestart = true;
		}

		if (shouldRestart) restart();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		final MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.main, menu);
		super.onCreateOptionsMenu(menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				menuDrawer.toggleMenu();
				return true;
			case R.id.main_shuffle:
				final Intent intent1 = new Intent(this, DownloadActivity.class);
				intent1.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
				startActivityForResultWithoutTransition(this, intent1);
				return true;
		}

		return false;
	}

	private void exit()
	{
		lifecycleSupport.getValue().onDestroy();
		Util.unregisterMediaButtonEventReceiver(this, false);
		finish();
	}

	private void showInfoDialog(final boolean show)
	{
		if (!infoDialogDisplayed)
		{
			infoDialogDisplayed = true;

			if (show)
			{
				Util.showWelcomeDialog(this, this, R.string.main_welcome_title, R.string.main_welcome_text);
			}
		}
	}

	private void showAlbumList(final String type, final int title)
	{
		final Intent intent = new Intent(this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, title);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxAlbums(this));
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showStarredSongs()
	{
		final Intent intent = new Intent(this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_STARRED, 1);
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showRandomSongs()
	{
		final Intent intent = new Intent(this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_RANDOM, 1);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxSongs(this));
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showArtists()
	{
		final Intent intent = new Intent(this, SelectArtistActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, getResources().getString(R.string.main_artists_title));
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showGenres()
	{
		final Intent intent = new Intent(this, SelectGenreActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showVideos()
	{
		final Intent intent = new Intent(this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_VIDEOS, 1);
		startActivityForResultWithoutTransition(this, intent);
	}

	private void showServers()
	{
		final Intent intent = new Intent(this, ServerSelectorActivity.class);
		startActivityForResult(intent, 0);
	}

	/**
     * Temporary task to make a ping to server to get it supported api version.
     */
    private static class PingTask extends TabActivityBackgroundTask<Void> {
        PingTask(SubsonicTabActivity activity, boolean changeProgress) {
            super(activity, changeProgress);
        }

        @Override
        protected Void doInBackground() throws Throwable {
            final MusicService service = MusicServiceFactory.getMusicService(getActivity());
            service.ping(getActivity(), null);
            return null;
        }

        @Override
        protected void done(Void result) {}
    }
}