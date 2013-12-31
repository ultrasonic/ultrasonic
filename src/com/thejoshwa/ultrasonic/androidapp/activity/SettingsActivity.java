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
package com.thejoshwa.ultrasonic.androidapp.activity;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.SearchRecentSuggestions;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.provider.SearchSuggestionProvider;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.ErrorDialog;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.ModalBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.TimeSpanPreference;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsActivity extends PreferenceResultActivity implements SharedPreferences.OnSharedPreferenceChangeListener, OnClickListener
{
	private static final String TAG = SettingsActivity.class.getSimpleName();
	private final Map<String, ServerSettings> serverSettings = new LinkedHashMap<String, ServerSettings>();
	private boolean testingConnection;
	private ListPreference theme;
	private ListPreference videoPlayer;
	private ListPreference maxBitrateWifi;
	private ListPreference maxBitrateMobile;
	private ListPreference cacheSize;
	private EditTextPreference cacheLocation;
	private ListPreference preloadCount;
	private ListPreference bufferLength;
	private ListPreference incrementTime;
	private ListPreference networkTimeout;
	private ListPreference maxAlbums;
	private ListPreference maxSongs;
	private ListPreference maxArtists;
	private ListPreference defaultAlbums;
	private ListPreference defaultSongs;
	private ListPreference defaultArtists;
	private ListPreference chatRefreshInterval;
	private ListPreference directoryCacheTime;
	private CheckBoxPreference mediaButtonsEnabled;
	private CheckBoxPreference lockScreenEnabled;
	private CheckBoxPreference sendBluetoothNotifications;
	private CheckBoxPreference sendBluetoothAlbumArt;
	private ListPreference viewRefresh;
	private EditTextPreference sharingDefaultDescription;
	private EditTextPreference sharingDefaultGreeting;
	private TimeSpanPreference sharingDefaultExpiration;
	private int maxServerCount = 10;
	private int minServerCount;

	private static final String STATE_MENUDRAWER = "com.thejoshwa.ultrasonic.androidapp.menuDrawer";
	private static final String STATE_ACTIVE_VIEW_ID = "com.thejoshwa.ultrasonic.androidapp.activeViewId";
	private static final String STATE_ACTIVE_POSITION = "com.thejoshwa.ultrasonic.androidapp.activePosition";

	public MenuDrawer menuDrawer;
	private int activePosition = 1;
	private int menuActiveViewId;
	private int activeServers = 3;
	View chatMenuItem;
	View bookmarksMenuItem;
	View sharesMenuItem;
	PreferenceCategory serversCategory;
	Preference addServerPreference;
	SharedPreferences settings;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		applyTheme();
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);

		if (savedInstanceState != null)
		{
			activePosition = savedInstanceState.getInt(STATE_ACTIVE_POSITION);
			menuActiveViewId = savedInstanceState.getInt(STATE_ACTIVE_VIEW_ID);
		}

		menuDrawer = MenuDrawer.attach(this, MenuDrawer.Type.BEHIND, Position.LEFT, MenuDrawer.MENU_DRAG_WINDOW);
		menuDrawer.setMenuView(R.layout.menu_main);

		chatMenuItem = findViewById(R.id.menu_chat);
		bookmarksMenuItem = findViewById(R.id.menu_bookmarks);
		sharesMenuItem = findViewById(R.id.menu_shares);

		findViewById(R.id.menu_home).setOnClickListener(this);
		findViewById(R.id.menu_browse).setOnClickListener(this);
		findViewById(R.id.menu_search).setOnClickListener(this);
		findViewById(R.id.menu_playlists).setOnClickListener(this);
		sharesMenuItem.setOnClickListener(this);
		chatMenuItem.setOnClickListener(this);
		bookmarksMenuItem.setOnClickListener(this);
		findViewById(R.id.menu_now_playing).setOnClickListener(this);
		findViewById(R.id.menu_settings).setOnClickListener(this);
		findViewById(R.id.menu_about).setOnClickListener(this);
		findViewById(R.id.menu_exit).setOnClickListener(this);

		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		View browseMenuItem = findViewById(R.id.menu_settings);
		menuDrawer.setActiveView(browseMenuItem);

		TextView activeView = (TextView) findViewById(menuActiveViewId);

		if (activeView != null)
		{
			menuDrawer.setActiveView(activeView);
		}

		theme = (ListPreference) findPreference(Constants.PREFERENCES_KEY_THEME);
		videoPlayer = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIDEO_PLAYER);
		maxBitrateWifi = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI);
		maxBitrateMobile = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE);
		cacheSize = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_SIZE);
		cacheLocation = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION);
		preloadCount = (ListPreference) findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT);
		bufferLength = (ListPreference) findPreference(Constants.PREFERENCES_KEY_BUFFER_LENGTH);
		incrementTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_INCREMENT_TIME);
		networkTimeout = (ListPreference) findPreference(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT);
		maxAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ALBUMS);
		maxSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_SONGS);
		maxArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ARTISTS);
		defaultArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS);
		defaultSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SONGS);
		defaultAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS);
		chatRefreshInterval = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL);
		directoryCacheTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME);
		mediaButtonsEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_MEDIA_BUTTONS);
		lockScreenEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS);
		sendBluetoothAlbumArt = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART);
		sendBluetoothNotifications = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS);
		viewRefresh = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIEW_REFRESH);
		sharingDefaultDescription = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION);
		sharingDefaultGreeting = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING);
		sharingDefaultExpiration = (TimeSpanPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION);

		sharingDefaultGreeting.setText(Util.getShareGreeting(this));

		Preference clearSearchPreference = findPreference(Constants.PREFERENCES_KEY_CLEAR_SEARCH_HISTORY);

		if (clearSearchPreference != null)
		{
			clearSearchPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					SearchRecentSuggestions suggestions = new SearchRecentSuggestions(SettingsActivity.this, SearchSuggestionProvider.AUTHORITY, SearchSuggestionProvider.MODE);
					suggestions.clearHistory();
					Util.toast(SettingsActivity.this, R.string.settings_search_history_cleared);
					return false;
				}
			});
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
		{
			PreferenceCategory playbackControlSettings = (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_PLAYBACK_CONTROL_SETTINGS);
			CheckBoxPreference gaplessPlaybackEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK);

			if (gaplessPlaybackEnabled != null)
			{
				gaplessPlaybackEnabled.setChecked(false);
				gaplessPlaybackEnabled.setEnabled(false);

				if (playbackControlSettings != null)
				{
					playbackControlSettings.removePreference(gaplessPlaybackEnabled);
				}
			}
		}

		settings = PreferenceManager.getDefaultSharedPreferences(this);
		activeServers = settings.getInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, 3);

		serversCategory = (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_SERVERS_KEY);

		addServerPreference = new Preference(this);
		addServerPreference.setKey(Constants.PREFERENCES_KEY_ADD_SERVER);
		addServerPreference.setPersistent(false);
		addServerPreference.setTitle(getResources().getString(R.string.settings_server_add_server));
		addServerPreference.setEnabled(activeServers < maxServerCount);

		serversCategory.addPreference(addServerPreference);

		for (int i = 1; i <= activeServers; i++)
		{
			final int instanceValue = i;

			serversCategory.addPreference(addServer(i));

			Preference testConnectionPreference = findPreference(Constants.PREFERENCES_KEY_TEST_CONNECTION + i);

			if (testConnectionPreference != null)
			{
				testConnectionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
				{
					@Override
					public boolean onPreferenceClick(Preference preference)
					{
						testConnection(instanceValue);
						return false;
					}
				});
			}

			String instance = String.valueOf(i);
			serverSettings.put(instance, new ServerSettings(instance));
		}

		addServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				if (activeServers == maxServerCount)
				{
					return false;
				}

				activeServers++;

				SharedPreferences.Editor prefEditor = settings.edit();
				prefEditor.putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers);
				prefEditor.commit();

				Preference addServerPreference = findPreference(Constants.PREFERENCES_KEY_ADD_SERVER);

				if (addServerPreference != null)
				{
					serversCategory.removePreference(addServerPreference);
				}

				serversCategory.addPreference(addServer(activeServers));

				if (addServerPreference != null)
				{
					serversCategory.addPreference(addServerPreference);
					addServerPreference.setEnabled(activeServers < maxServerCount);
				}

				String instance = String.valueOf(activeServers);
				serverSettings.put(instance, new ServerSettings(instance));

				applyTheme();

				return true;
			}
		});

		SharedPreferences preferences = Util.getPreferences(this);
		preferences.registerOnSharedPreferenceChangeListener(this);

		if (actionBar != null)
		{
			actionBar.setSubtitle(R.string.menu_settings);
		}

		update();
	}

	@Override
	protected void onPostCreate(Bundle bundle)
	{
		super.onPostCreate(bundle);

		int visibility = Util.isOffline(this) ? View.GONE : View.VISIBLE;
		chatMenuItem.setVisibility(visibility);
		bookmarksMenuItem.setVisibility(visibility);
		sharesMenuItem.setVisibility(visibility);
	}

	private PreferenceScreen addServer(final int instance)
	{
		final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);

		if (screen != null)
		{
			screen.setTitle(R.string.settings_server_unused);
			screen.setKey(Constants.PREFERENCES_KEY_SERVER + instance);
		}

		final EditTextPreference serverNamePreference = new EditTextPreference(this);
		serverNamePreference.setKey(Constants.PREFERENCES_KEY_SERVER_NAME + instance);
		serverNamePreference.setDefaultValue(getResources().getString(R.string.settings_server_unused));
		serverNamePreference.setTitle(R.string.settings_server_name);

		if (serverNamePreference.getText() == null)
		{
			serverNamePreference.setText(getResources().getString(R.string.settings_server_unused));
		}

		serverNamePreference.setSummary(serverNamePreference.getText());

		final EditTextPreference serverUrlPreference = new EditTextPreference(this);
		serverUrlPreference.setKey(Constants.PREFERENCES_KEY_SERVER_URL + instance);
		serverUrlPreference.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_URI);
		serverUrlPreference.setDefaultValue("http://yourhost");
		serverUrlPreference.setTitle(R.string.settings_server_address);

		if (serverUrlPreference.getText() == null)
		{
			serverUrlPreference.setText("http://yourhost");
		}

		serverUrlPreference.setSummary(serverUrlPreference.getText());

		if (screen != null)
		{
			screen.setSummary(serverUrlPreference.getText());
		}

		final EditTextPreference serverUsernamePreference = new EditTextPreference(this);
		serverUsernamePreference.setKey(Constants.PREFERENCES_KEY_USERNAME + instance);
		serverUsernamePreference.setTitle(R.string.settings_server_username);

		final EditTextPreference serverPasswordPreference = new EditTextPreference(this);
		serverPasswordPreference.setKey(Constants.PREFERENCES_KEY_PASSWORD + instance);
		serverPasswordPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		serverPasswordPreference.setSummary("***");
		serverPasswordPreference.setTitle(R.string.settings_server_password);

		final CheckBoxPreference serverEnabledPreference = new CheckBoxPreference(this);
		serverEnabledPreference.setDefaultValue(true);
		serverEnabledPreference.setKey(Constants.PREFERENCES_KEY_SERVER_ENABLED + instance);
		serverEnabledPreference.setTitle(R.string.equalizer_enabled);

		final CheckBoxPreference jukeboxEnabledPreference = new CheckBoxPreference(this);
		jukeboxEnabledPreference.setDefaultValue(false);
		jukeboxEnabledPreference.setKey(Constants.PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + instance);
		jukeboxEnabledPreference.setTitle(R.string.jukebox_is_default);

		Preference serverRemoveServerPreference = new Preference(this);
		serverRemoveServerPreference.setKey(Constants.PREFERENCES_KEY_REMOVE_SERVER + instance);
		serverRemoveServerPreference.setPersistent(false);
		serverRemoveServerPreference.setTitle(R.string.settings_server_remove_server);

		serverRemoveServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{

				if (activeServers == minServerCount)
				{
					return false;
				}

				// Reset values to null so when we ask for them again they are new
				serverNamePreference.setText(null);
				serverUrlPreference.setText(null);
				serverUsernamePreference.setText(null);
				serverPasswordPreference.setText(null);
				serverEnabledPreference.setChecked(true);
				jukeboxEnabledPreference.setChecked(false);

				if (instance < activeServers)
				{

					int activeServer = Util.getActiveServer(SettingsActivity.this);
					for (int i = instance; i <= activeServers; i++)
					{
						Util.removeInstanceName(SettingsActivity.this, i, activeServer);
					}
				}

				activeServers--;

				if (screen != null)
				{
					serversCategory.removePreference(screen);
					Dialog dialog = screen.getDialog();

					if (dialog != null)
					{
						dialog.dismiss();
					}
				}

				SharedPreferences.Editor prefEditor = settings.edit();
				prefEditor.putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers);
				prefEditor.commit();

				addServerPreference.setEnabled(activeServers < maxServerCount);

				return true;
			}
		});

		Preference serverTestConnectionPreference = new Preference(this);
		serverTestConnectionPreference.setKey(Constants.PREFERENCES_KEY_TEST_CONNECTION + instance);
		serverTestConnectionPreference.setPersistent(false);
		serverTestConnectionPreference.setTitle(R.string.settings_test_connection_title);

		serverTestConnectionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				testConnection(instance);
				return false;
			}
		});

		if (screen != null)
		{
			screen.addPreference(serverNamePreference);
			screen.addPreference(serverUrlPreference);
			screen.addPreference(serverUsernamePreference);
			screen.addPreference(serverPasswordPreference);
			screen.addPreference(serverEnabledPreference);
			screen.addPreference(jukeboxEnabledPreference);
			screen.addPreference(serverRemoveServerPreference);
			screen.addPreference(serverTestConnectionPreference);
		}

		return screen;
	}

	private void applyTheme()
	{
		String theme = Util.getTheme(this);

		// Support the old fullscreen themes as well, for upgrade purposes
		if ("dark".equalsIgnoreCase(theme) || "fullscreen".equalsIgnoreCase(theme))
		{
			setTheme(R.style.UltraSonicTheme);
		}
		else if ("light".equalsIgnoreCase(theme) || "fullscreenlight".equalsIgnoreCase(theme))
		{
			setTheme(R.style.UltraSonicTheme_Light);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		SharedPreferences prefs = Util.getPreferences(this);
		prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Log.d(TAG, "Preference changed: " + key);
		update();

		if (Constants.PREFERENCES_KEY_HIDE_MEDIA.equals(key))
		{
			setHideMedia(sharedPreferences.getBoolean(key, false));
		}
		else if (Constants.PREFERENCES_KEY_MEDIA_BUTTONS.equals(key))
		{
			setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true));
		}
		else if (Constants.PREFERENCES_KEY_CACHE_LOCATION.equals(key))
		{
			setCacheLocation(sharedPreferences.getString(key, ""));
		}
		else if (Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS.equals(key))
		{
			setBluetoothPreferences(sharedPreferences.getBoolean(key, true));
		}
	}

	private void update()
	{
		if (testingConnection)
		{
			return;
		}

		theme.setSummary(theme.getEntry());
		videoPlayer.setSummary(videoPlayer.getEntry());
		maxBitrateWifi.setSummary(maxBitrateWifi.getEntry());
		maxBitrateMobile.setSummary(maxBitrateMobile.getEntry());
		cacheSize.setSummary(cacheSize.getEntry());
		cacheLocation.setSummary(cacheLocation.getText());
		preloadCount.setSummary(preloadCount.getEntry());
		bufferLength.setSummary(bufferLength.getEntry());
		incrementTime.setSummary(incrementTime.getEntry());
		networkTimeout.setSummary(networkTimeout.getEntry());
		maxAlbums.setSummary(maxAlbums.getEntry());
		maxArtists.setSummary(maxArtists.getEntry());
		maxSongs.setSummary(maxSongs.getEntry());
		defaultAlbums.setSummary(defaultAlbums.getEntry());
		defaultArtists.setSummary(defaultArtists.getEntry());
		defaultSongs.setSummary(defaultSongs.getEntry());
		chatRefreshInterval.setSummary(chatRefreshInterval.getEntry());
		directoryCacheTime.setSummary(directoryCacheTime.getEntry());
		viewRefresh.setSummary(viewRefresh.getEntry());
		sharingDefaultExpiration.setSummary(sharingDefaultExpiration.getText());
		sharingDefaultDescription.setSummary(sharingDefaultDescription.getText());
		sharingDefaultGreeting.setSummary(sharingDefaultGreeting.getText());

		if (!mediaButtonsEnabled.isChecked())
		{
			lockScreenEnabled.setChecked(false);
			lockScreenEnabled.setEnabled(false);
		}

		if (!sendBluetoothNotifications.isChecked())
		{
			sendBluetoothAlbumArt.setChecked(false);
			sendBluetoothAlbumArt.setEnabled(false);
		}

		for (ServerSettings ss : serverSettings.values())
		{
			ss.update();
		}
	}

	private void setHideMedia(boolean hide)
	{
		File nomediaDir = new File(FileUtil.getUltraSonicDirectory(), ".nomedia");
		if (hide && !nomediaDir.exists())
		{
			if (!nomediaDir.mkdir())
			{
				Log.w(TAG, "Failed to create " + nomediaDir);
			}
		}
		else if (nomediaDir.exists())
		{
			if (!nomediaDir.delete())
			{
				Log.w(TAG, "Failed to delete " + nomediaDir);
			}
		}
		Util.toast(this, R.string.settings_hide_media_toast, false);
	}

	private void setMediaButtonsEnabled(boolean enabled)
	{
		if (enabled)
		{
			lockScreenEnabled.setEnabled(true);
			Util.registerMediaButtonEventReceiver(this);
		}
		else
		{
			lockScreenEnabled.setEnabled(false);
			Util.unregisterMediaButtonEventReceiver(this);
		}
	}

	private void setBluetoothPreferences(boolean enabled)
	{
		if (enabled)
		{
			sendBluetoothAlbumArt.setEnabled(true);
		}
		else
		{
			sendBluetoothAlbumArt.setEnabled(false);
		}
	}

	private void setCacheLocation(String path)
	{
		File dir = new File(path);

		if (!FileUtil.ensureDirectoryExistsAndIsReadWritable(dir))
		{
			Util.toast(this, R.string.settings_cache_location_error, false);

			// Reset it to the default.
			String defaultPath = FileUtil.getDefaultMusicDirectory().getPath();
			if (!defaultPath.equals(path))
			{
				SharedPreferences prefs = Util.getPreferences(this);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, defaultPath);
				editor.commit();
				cacheLocation.setSummary(defaultPath);
				cacheLocation.setText(defaultPath);
			}

			// Clear download queue.
			DownloadService downloadService = DownloadServiceImpl.getInstance();
			downloadService.clear();
		}
	}

	private void testConnection(final int instance)
	{
		ModalBackgroundTask<Boolean> task = new ModalBackgroundTask<Boolean>(this, false)
		{
			private int previousInstance;

			@Override
			protected Boolean doInBackground() throws Throwable
			{
				updateProgress(R.string.settings_testing_connection);

				previousInstance = Util.getActiveServer(SettingsActivity.this);
				testingConnection = true;
				Util.setActiveServer(SettingsActivity.this, instance);
				try
				{
					MusicService musicService = MusicServiceFactory.getMusicService(SettingsActivity.this);
					musicService.ping(SettingsActivity.this, this);
					return musicService.isLicenseValid(SettingsActivity.this, null);
				}
				finally
				{
					Util.setActiveServer(SettingsActivity.this, previousInstance);
					testingConnection = false;
				}
			}

			@Override
			protected void done(Boolean licenseValid)
			{
				if (licenseValid)
				{
					Util.toast(SettingsActivity.this, R.string.settings_testing_ok);
				}
				else
				{
					Util.toast(SettingsActivity.this, R.string.settings_testing_unlicensed);
				}
			}

			@Override
			protected void cancel()
			{
				super.cancel();
				Util.setActiveServer(SettingsActivity.this, previousInstance);
			}

			@Override
			protected void error(Throwable error)
			{
				Log.w(TAG, error.toString(), error);
				new ErrorDialog(SettingsActivity.this, String.format("%s %s", getResources().getString(R.string.settings_connection_failure), getErrorMessage(error)), false);
			}
		};
		task.execute();
	}

	private class ServerSettings
	{
		private EditTextPreference serverName;
		private EditTextPreference serverUrl;
		private EditTextPreference username;
		private PreferenceScreen screen;

		private ServerSettings(String instance)
		{

			screen = (PreferenceScreen) findPreference("server" + instance);
			serverName = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_SERVER_NAME + instance);
			serverUrl = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_SERVER_URL + instance);
			username = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_USERNAME + instance);

			serverUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
			{
				@Override
				public boolean onPreferenceChange(Preference preference, Object value)
				{
					try
					{
						String url = (String) value;
						new URL(url);
						if (!url.equals(url.trim()) || url.contains("@"))
						{
							throw new Exception();
						}
					}
					catch (Exception x)
					{
						new ErrorDialog(SettingsActivity.this, R.string.settings_invalid_url, false);
						return false;
					}
					return true;
				}
			});

			username.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
			{
				@Override
				public boolean onPreferenceChange(Preference preference, Object value)
				{
					String username = (String) value;
					if (username == null || !username.equals(username.trim()))
					{
						new ErrorDialog(SettingsActivity.this, R.string.settings_invalid_username, false);
						return false;
					}
					return true;
				}
			});
		}

		public void update()
		{
			serverName.setSummary(serverName.getText());
			serverUrl.setSummary(serverUrl.getText());
			username.setSummary(username.getText());
			screen.setSummary(serverUrl.getText());
			screen.setTitle(serverName.getText());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				menuDrawer.toggleMenu();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onRestoreInstanceState(Bundle inState)
	{
		super.onRestoreInstanceState(inState);
		menuDrawer.restoreState(inState.getParcelable(STATE_MENUDRAWER));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putParcelable(STATE_MENUDRAWER, menuDrawer.saveState());
		outState.putInt(STATE_ACTIVE_VIEW_ID, menuActiveViewId);
		outState.putInt(STATE_ACTIVE_POSITION, activePosition);
	}

	@Override
	public void onBackPressed()
	{
		final int drawerState = menuDrawer.getDrawerState();

		if (drawerState == MenuDrawer.STATE_OPEN || drawerState == MenuDrawer.STATE_OPENING)
		{
			menuDrawer.closeMenu(true);
			return;
		}

		super.onBackPressed();
	}

	@Override
	public void onClick(View v)
	{
		menuActiveViewId = v.getId();
		menuDrawer.setActiveView(v);

		Intent intent;

		switch (menuActiveViewId)
		{
			case R.id.menu_home:
				intent = new Intent(this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResultWithoutTransition(this, intent);
				break;
			case R.id.menu_browse:
				intent = new Intent(this, SelectArtistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResultWithoutTransition(this, intent);
				break;
			case R.id.menu_search:
				intent = new Intent(this, SearchActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
				startActivityForResultWithoutTransition(this, intent);
				break;
			case R.id.menu_playlists:
				intent = new Intent(this, SelectPlaylistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResultWithoutTransition(this, intent);
				break;
			case R.id.menu_shares:
				intent = new Intent(this, ShareActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResultWithoutTransition(this, intent);
				break;
			case R.id.menu_chat:
				startActivityForResultWithoutTransition(this, ChatActivity.class);
				break;
			case R.id.menu_bookmarks:
				startActivityForResultWithoutTransition(this, BookmarkActivity.class);
				break;
			case R.id.menu_now_playing:
				startActivityForResultWithoutTransition(this, DownloadActivity.class);
				break;
			case R.id.menu_settings:
				startActivityForResultWithoutTransition(this, SettingsActivity.class);
				break;
			case R.id.menu_about:
				startActivityForResultWithoutTransition(this, HelpActivity.class);
				break;
			case R.id.menu_exit:
				intent = new Intent(this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
				startActivityForResultWithoutTransition(this, intent);
				break;
		}

		menuDrawer.closeMenu(true);
	}
}