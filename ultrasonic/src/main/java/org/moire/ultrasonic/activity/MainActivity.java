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
import org.moire.ultrasonic.data.ServerSetting;
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.MergeAdapter;
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
	private static String lastActiveServerProperties;

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

		setContentView(R.layout.main);

		final View homeMenuItem = findViewById(R.id.menu_home);
		menuDrawer.setActiveView(homeMenuItem);

		setActionBarTitle(R.string.common_appname);
		setTitle(R.string.common_appname);

		// Remember the current theme.
		theme = Util.getTheme(this);
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
}