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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.service.MediaPlayerController;
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
import static org.koin.java.standalone.KoinJavaComponent.inject;

public class MainActivity extends SubsonicTabActivity
{

	private static final int MENU_GROUP_SERVER = 10;
	private static final int MENU_ITEM_OFFLINE = 111;
	private static final int MENU_ITEM_SERVER_1 = 101;
	private static final int MENU_ITEM_SERVER_2 = 102;
	private static final int MENU_ITEM_SERVER_3 = 103;
	private static final int MENU_ITEM_SERVER_4 = 104;
	private static final int MENU_ITEM_SERVER_5 = 105;
	private static final int MENU_ITEM_SERVER_6 = 106;
	private static final int MENU_ITEM_SERVER_7 = 107;
	private static final int MENU_ITEM_SERVER_8 = 108;
	private static final int MENU_ITEM_SERVER_9 = 109;
	private static final int MENU_ITEM_SERVER_10 = 110;

	private static boolean infoDialogDisplayed;
	private static boolean shouldUseId3;

	private Lazy<MediaPlayerLifecycleSupport> lifecycleSupport = inject(MediaPlayerLifecycleSupport.class);

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

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
		final TextView serverTextView = (TextView) serverButton.findViewById(R.id.main_select_server_2);
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
		final View dummyView = findViewById(R.id.main_dummy);

		boolean shouldShowDialog = false;

		if (!getActiveServerEnabled())
		{
			shouldShowDialog = true;
			Util.setActiveServer(this, 0);
		}

		int instance = Util.getActiveServer(this);
		String name = Util.getServerName(this, instance);

		if (name == null)
		{
			shouldShowDialog = true;
			Util.setActiveServer(this, 0);
			instance = Util.getActiveServer(this);
			name = Util.getServerName(this, instance);
		}

		serverTextView.setText(name);

		final ListView list = (ListView) findViewById(R.id.main_list);

		final MergeAdapter adapter = new MergeAdapter();
		adapter.addViews(Collections.singletonList(serverButton), true);

		if (!Util.isOffline(this))
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
		registerForContextMenu(dummyView);

		list.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (view == serverButton)
				{
					dummyView.showContextMenu();
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

		showInfoDialog(shouldShowDialog);
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

		boolean id3 = Util.getShouldUseId3Tags(MainActivity.this);

		if (id3 != shouldUseId3)
		{
			shouldUseId3 = id3;
			restart();
		}
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
	public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, view, menuInfo);

		final int activeServer = Util.getActiveServer(this);
		boolean checked = false;

		for (int i = 0; i <= Util.getActiveServers(this); i++)
		{
			final String serverName = Util.getServerName(this, i);

			if (serverName == null)
			{
				continue;
			}

			if (Util.getServerEnabled(this, i))
			{
				final int menuItemNum = getMenuItem(i);

				final MenuItem menuItem = menu.add(MENU_GROUP_SERVER, menuItemNum, menuItemNum, serverName);

				if (activeServer == i)
				{
					checked = true;
					menuItem.setChecked(true);
				}
			}
		}

		if (!checked)
		{
			MenuItem menuItem = menu.findItem(getMenuItem(0));

			if (menuItem != null)
			{
				menuItem.setChecked(true);
			}
		}

		menu.setGroupCheckable(MENU_GROUP_SERVER, true, true);
		menu.setHeaderTitle(R.string.main_select_server);
	}

	private boolean getActiveServerEnabled()
	{
		final int activeServer = Util.getActiveServer(this);
		boolean activeServerEnabled = false;

		for (int i = 0; i <= Util.getActiveServers(this); i++)
		{
			if (Util.getServerEnabled(this, i))
			{
				if (activeServer == i)
				{
					activeServerEnabled = true;
				}
			}
		}

		return activeServerEnabled;
	}

	private static int getMenuItem(final int serverInstance)
	{
		switch (serverInstance)
		{
			case 0:
				return MENU_ITEM_OFFLINE;
			case 1:
				return MENU_ITEM_SERVER_1;
			case 2:
				return MENU_ITEM_SERVER_2;
			case 3:
				return MENU_ITEM_SERVER_3;
			case 4:
				return MENU_ITEM_SERVER_4;
			case 5:
				return MENU_ITEM_SERVER_5;
			case 6:
				return MENU_ITEM_SERVER_6;
			case 7:
				return MENU_ITEM_SERVER_7;
			case 8:
				return MENU_ITEM_SERVER_8;
			case 9:
				return MENU_ITEM_SERVER_9;
			case 10:
				return MENU_ITEM_SERVER_10;
		}

		return 0;
	}

	@Override
	public boolean onContextItemSelected(final MenuItem menuItem)
	{
		switch (menuItem.getItemId())
		{
			case MENU_ITEM_OFFLINE:
				setActiveServer(0);
				break;
			case MENU_ITEM_SERVER_1:
				setActiveServer(1);
				break;
			case MENU_ITEM_SERVER_2:
				setActiveServer(2);
				break;
			case MENU_ITEM_SERVER_3:
				setActiveServer(3);
				break;
			case MENU_ITEM_SERVER_4:
				setActiveServer(4);
				break;
			case MENU_ITEM_SERVER_5:
				setActiveServer(5);
				break;
			case MENU_ITEM_SERVER_6:
				setActiveServer(6);
				break;
			case MENU_ITEM_SERVER_7:
				setActiveServer(7);
				break;
			case MENU_ITEM_SERVER_8:
				setActiveServer(8);
				break;
			case MENU_ITEM_SERVER_9:
				setActiveServer(9);
				break;
			case MENU_ITEM_SERVER_10:
				setActiveServer(10);
				break;
			default:
				return super.onContextItemSelected(menuItem);
		}

		// Restart activity
		restart();
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

	private void setActiveServer(final int instance)
	{
		final MediaPlayerController service = getMediaPlayerController();

		if (Util.getActiveServer(this) != instance)
		{
			if (service != null)
			{
				service.clearIncomplete();
			}
		}

		Util.setActiveServer(this, instance);

		if (service != null)
		{
			service.setJukeboxEnabled(Util.getJukeboxEnabled(this, instance));
		}
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

			if (show || Util.getRestUrl(this, null).contains("yourhost"))
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