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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.AlbumHeader;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator;
import org.moire.ultrasonic.util.Pair;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.AlbumView;
import org.moire.ultrasonic.view.EntryAdapter;
import org.moire.ultrasonic.view.SongView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class SelectAlbumActivity extends SubsonicTabActivity
{

	public static final String allSongsId = "-1";
	private SwipeRefreshLayout refreshAlbumListView;
	private ListView albumListView;
	private View header;
	private View albumButtons;
	private View emptyView;
	private ImageView selectButton;
	private ImageView playNowButton;
	private ImageView playNextButton;
	private ImageView playLastButton;
	private ImageView pinButton;
	private ImageView unpinButton;
	private ImageView downloadButton;
	private ImageView deleteButton;
	private ImageView moreButton;
	private boolean playAllButtonVisible;
	private boolean shareButtonVisible;
	private MenuItem playAllButton;
	private MenuItem shareButton;
	private boolean showHeader = true;
	private Random random = new java.security.SecureRandom();

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.select_album);


	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		playAllButton = menu.findItem(R.id.select_album_play_all);

		if (playAllButton != null)
		{
			playAllButton.setVisible(playAllButtonVisible);
		}

		shareButton = menu.findItem(R.id.menu_item_share);

		if (shareButton != null)
		{
			shareButton.setVisible(shareButtonVisible);
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.select_album, menu);
		super.onCreateOptionsMenu(menu);

		return true;
	}




	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				menuDrawer.toggleMenu();
				return true;
			case R.id.main_shuffle:
				Intent intent1 = new Intent(this, DownloadActivity.class);
				intent1.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
				startActivityForResultWithoutTransition(this, intent1);
				return true;
			case R.id.select_album_play_all:
				// TODO
				//playAll();
				return true;
			case R.id.menu_item_share:
				// TODO
				//createShare(getSelectedSongs(albumListView));
				return true;
		}

		return false;
	}


}
