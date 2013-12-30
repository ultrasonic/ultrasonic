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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.service.OfflineException;
import com.thejoshwa.ultrasonic.androidapp.service.ServerTooOldException;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.CacheCleaner;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.LoadingTask;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.view.PlaylistAdapter;

import java.util.List;

public class SelectPlaylistActivity extends SubsonicTabActivity implements AdapterView.OnItemClickListener
{

	private PullToRefreshListView refreshPlaylistsListView;
	private ListView playlistsListView;
	private View emptyTextView;
	private PlaylistAdapter playlistAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.select_playlist);

		refreshPlaylistsListView = (PullToRefreshListView) findViewById(R.id.select_playlist_list);
		playlistsListView = refreshPlaylistsListView.getRefreshableView();

		refreshPlaylistsListView.setOnRefreshListener(new OnRefreshListener<ListView>()
		{
			@Override
			public void onRefresh(PullToRefreshBase<ListView> refreshView)
			{
				new GetDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		});

		emptyTextView = findViewById(R.id.select_playlist_empty);
		playlistsListView.setOnItemClickListener(this);
		registerForContextMenu(playlistsListView);

		View playlistsMenuItem = findViewById(R.id.menu_playlists);
		menuDrawer.setActiveView(playlistsMenuItem);

		setActionBarTitle(R.string.common_appname);
		setActionBarSubtitle(R.string.playlist_label);

		load();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		return true;
	}

	private void refresh()
	{
		finish();
		Intent intent = new Intent(this, SelectPlaylistActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
		Util.startActivityWithoutTransition(this, intent);
	}

	private void load()
	{
		BackgroundTask<List<Playlist>> task = new TabActivityBackgroundTask<List<Playlist>>(this, true)
		{
			@Override
			protected List<Playlist> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(SelectPlaylistActivity.this);
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				List<Playlist> playlists = musicService.getPlaylists(refresh, SelectPlaylistActivity.this, this);

				if (!Util.isOffline(SelectPlaylistActivity.this))
					new CacheCleaner(SelectPlaylistActivity.this, getDownloadService()).cleanPlaylists(playlists);
				return playlists;
			}

			@Override
			protected void done(List<Playlist> result)
			{
				playlistsListView.setAdapter(playlistAdapter = new PlaylistAdapter(SelectPlaylistActivity.this, result));
				emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
			}
		};
		task.execute();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, view, menuInfo);

		MenuInflater inflater = getMenuInflater();
		if (Util.isOffline(this)) inflater.inflate(R.menu.select_playlist_context_offline, menu);
		else inflater.inflate(R.menu.select_playlist_context, menu);

		MenuItem downloadMenuItem = menu.findItem(R.id.album_menu_download);

		if (downloadMenuItem != null)
		{
			downloadMenuItem.setVisible(!Util.isOffline(this));
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuItem)
	{
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
		if (info == null)
		{
			return false;
		}

		Playlist playlist = (Playlist) playlistsListView.getItemAtPosition(info.position);
		if (playlist == null)
		{
			return false;
		}

		Intent intent;
		switch (menuItem.getItemId())
		{
			case R.id.playlist_menu_pin:
				downloadPlaylist(playlist.getId(), playlist.getName(), true, true, false, false, true, false, false);
				break;
			case R.id.playlist_menu_unpin:
				downloadPlaylist(playlist.getId(), playlist.getName(), false, false, false, false, true, false, true);
				break;
			case R.id.playlist_menu_download:
				downloadPlaylist(playlist.getId(), playlist.getName(), false, false, false, false, true, false, false);
				break;
			case R.id.playlist_menu_play_now:
				intent = new Intent(SelectPlaylistActivity.this, SelectAlbumActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.getId());
				intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.getName());
				intent.putExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, true);
				Util.startActivityWithoutTransition(SelectPlaylistActivity.this, intent);
				break;
			case R.id.playlist_menu_play_shuffled:
				intent = new Intent(SelectPlaylistActivity.this, SelectAlbumActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.getId());
				intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.getName());
				intent.putExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, true);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
				Util.startActivityWithoutTransition(SelectPlaylistActivity.this, intent);
				break;
			case R.id.playlist_menu_delete:
				deletePlaylist(playlist);
				break;
			case R.id.playlist_info:
				displayPlaylistInfo(playlist);
				break;
			case R.id.playlist_update_info:
				updatePlaylistInfo(playlist);
				break;
			default:
				return super.onContextItemSelected(menuItem);
		}
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
		}

		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		Playlist playlist = (Playlist) parent.getItemAtPosition(position);

		if (playlist == null)
		{
			return;
		}

		Intent intent = new Intent(SelectPlaylistActivity.this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, playlist.getId());
		intent.putExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME, playlist.getName());
		Util.startActivityWithoutTransition(SelectPlaylistActivity.this, intent);
	}

	private void deletePlaylist(final Playlist playlist)
	{
		new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.common_confirm).setMessage(getResources().getString(R.string.delete_playlist, playlist.getName())).setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				new LoadingTask<Void>(SelectPlaylistActivity.this, false)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						MusicService musicService = MusicServiceFactory.getMusicService(SelectPlaylistActivity.this);
						musicService.deletePlaylist(playlist.getId(), SelectPlaylistActivity.this, null);
						return null;
					}

					@Override
					protected void done(Void result)
					{
						playlistAdapter.remove(playlist);
						playlistAdapter.notifyDataSetChanged();
						Util.toast(SelectPlaylistActivity.this, getResources().getString(R.string.menu_deleted_playlist, playlist.getName()));
					}

					@Override
					protected void error(Throwable error)
					{
						String msg;
						msg = error instanceof OfflineException || error instanceof ServerTooOldException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.menu_deleted_playlist_error, playlist.getName()), getErrorMessage(error));

						Util.toast(SelectPlaylistActivity.this, msg, false);
					}
				}.execute();
			}

		}).setNegativeButton(R.string.common_cancel, null).show();
	}

	private void displayPlaylistInfo(final Playlist playlist)
	{
		String message = "Owner: " + playlist.getOwner() + "\nComments: " +
				((playlist.getComment() == null) ? "" : playlist.getComment()) +
				"\nSong Count: " + playlist.getSongCount() +
				((playlist.getPublic() == null) ? "" : ("\nPublic: " + playlist.getPublic()) + ((playlist.getCreated() == null) ? "" : ("\nCreation Date: " + playlist.getCreated().replace('T', ' '))));
		new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(playlist.getName()).setMessage(message).show();
	}

	private void updatePlaylistInfo(final Playlist playlist)
	{
		View dialogView = getLayoutInflater().inflate(R.layout.update_playlist, null);

		if (dialogView == null)
		{
			return;
		}

		final EditText nameBox = (EditText) dialogView.findViewById(R.id.get_playlist_name);
		final EditText commentBox = (EditText) dialogView.findViewById(R.id.get_playlist_comment);
		final CheckBox publicBox = (CheckBox) dialogView.findViewById(R.id.get_playlist_public);

		nameBox.setText(playlist.getName());
		commentBox.setText(playlist.getComment());
		Boolean pub = playlist.getPublic();

		if (pub == null)
		{
			publicBox.setEnabled(false);
		}
		else
		{
			publicBox.setChecked(pub);
		}

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

		alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
		alertDialog.setTitle(R.string.playlist_update_info);
		alertDialog.setView(dialogView);
		alertDialog.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				new LoadingTask<Void>(SelectPlaylistActivity.this, false)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						Editable nameBoxText = nameBox.getText();
						Editable commentBoxText = commentBox.getText();
						String name = nameBoxText != null ? nameBoxText.toString() : null;
						String comment = commentBoxText != null ? commentBoxText.toString() : null;

						MusicService musicService = MusicServiceFactory.getMusicService(SelectPlaylistActivity.this);
						musicService.updatePlaylist(playlist.getId(), name, comment, publicBox.isChecked(), SelectPlaylistActivity.this, null);
						return null;
					}

					@Override
					protected void done(Void result)
					{
						refresh();
						Util.toast(SelectPlaylistActivity.this, getResources().getString(R.string.playlist_updated_info, playlist.getName()));
					}

					@Override
					protected void error(Throwable error)
					{
						String msg;
						msg = error instanceof OfflineException || error instanceof ServerTooOldException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.playlist_updated_info_error, playlist.getName()), getErrorMessage(error));

						Util.toast(SelectPlaylistActivity.this, msg, false);
					}
				}.execute();
			}

		});
		alertDialog.setNegativeButton(R.string.common_cancel, null);
		alertDialog.show();
	}

	private class GetDataTask extends AsyncTask<Void, Void, String[]>
	{
		@Override
		protected void onPostExecute(String[] result)
		{
			refreshPlaylistsListView.onRefreshComplete();
			super.onPostExecute(result);
		}

		@Override
		protected String[] doInBackground(Void... params)
		{
			refresh();
			return null;
		}
	}
}