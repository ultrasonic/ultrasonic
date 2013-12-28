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

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.AlbumHeader;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.EntryByDiscAndTrackComparator;
import com.thejoshwa.ultrasonic.androidapp.util.Pair;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.view.EntryAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;

public class SelectAlbumActivity extends SubsonicTabActivity
{

	private PullToRefreshListView refreshAlbumListView;
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
	private Random random = new Random();

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.select_album);

		albumButtons = findViewById(R.id.menu_album);

		refreshAlbumListView = (PullToRefreshListView) findViewById(R.id.select_album_entries);
		albumListView = refreshAlbumListView.getRefreshableView();

		refreshAlbumListView.setOnRefreshListener(new OnRefreshListener<ListView>()
		{
			@Override
			public void onRefresh(PullToRefreshBase<ListView> refreshView)
			{
				new GetDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		});

		header = LayoutInflater.from(this).inflate(R.layout.select_album_header, albumListView, false);

		albumListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		albumListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (position >= 0)
				{
					MusicDirectory.Entry entry = (MusicDirectory.Entry) parent.getItemAtPosition(position);
					if (entry != null && entry.isDirectory())
					{
						Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
						intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, entry.getId());
						intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, entry.isDirectory());
						intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.getTitle());
						Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
					}
					else if (entry != null && entry.isVideo())
					{
						playVideo(entry);
					}
					else
					{
						enableButtons();
					}
				}
			}
		});

		selectButton = (ImageView) findViewById(R.id.select_album_select);
		playNowButton = (ImageView) findViewById(R.id.select_album_play_now);
		playNextButton = (ImageView) findViewById(R.id.select_album_play_next);
		playLastButton = (ImageView) findViewById(R.id.select_album_play_last);
		pinButton = (ImageView) findViewById(R.id.select_album_pin);
		unpinButton = (ImageView) findViewById(R.id.select_album_unpin);
		downloadButton = (ImageView) findViewById(R.id.select_album_download);
		deleteButton = (ImageView) findViewById(R.id.select_album_delete);
		moreButton = (ImageView) findViewById(R.id.select_album_more);
		emptyView = findViewById(R.id.select_album_empty);

		selectButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				selectAllOrNone();
			}
		});
		playNowButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				playNow(false, false);
			}
		});
		playNextButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				download(true, false, false, true, false, getSelectedSongs(albumListView));
				selectAll(false, false);
			}
		});
		playLastButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				playNow(false, true);
			}
		});
		pinButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				downloadBackground(true);
				selectAll(false, false);
			}
		});
		unpinButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				unpin();
				selectAll(false, false);
			}
		});
		downloadButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				downloadBackground(false);
				selectAll(false, false);
			}
		});
		deleteButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				delete();
				selectAll(false, false);
			}
		});

		registerForContextMenu(albumListView);

		enableButtons();

		String id = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID);
		boolean isAlbum = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false);
		String name = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_NAME);
		String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
		String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
		String shareId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_SHARE_ID);
		String shareName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_SHARE_NAME);
		String albumListType = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
		String genreName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_GENRE_NAME);
		int albumListTitle = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0);
		int getStarredTracks = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_STARRED, 0);
		int getVideos = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_VIDEOS, 0);
		int getRandomTracks = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_RANDOM, 0);
		int albumListSize = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
		int albumListOffset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);

		View browseMenuItem = findViewById(R.id.menu_browse);
		menuDrawer.setActiveView(browseMenuItem);

		if (playlistId != null)
		{
			getPlaylist(playlistId, playlistName);
		}
		else if (shareId != null)
		{
			getShare(shareId, shareName);
		}
		else if (albumListType != null)
		{
			getAlbumList(albumListType, albumListTitle, albumListSize, albumListOffset);
		}
		else if (genreName != null)
		{
			getSongsForGenre(genreName, albumListSize, albumListOffset);
		}
		else if (getStarredTracks != 0)
		{
			getStarred();
		}
		else if (getVideos != 0)
		{
			getVideos();
		}
		else if (getRandomTracks != 0)
		{
			getRandom(albumListSize);
		}
		else
		{
			if (!Util.isOffline(SelectAlbumActivity.this) && Util.getShouldUseId3Tags(SelectAlbumActivity.this))
			{
				if (isAlbum)
				{
					getAlbum(id, name);
				}
				else
				{
					getArtist(id, name);
				}
			}
			else
			{
				getMusicDirectory(id, name);
			}
		}
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

	private void playNow(final boolean shuffle, final boolean append)
	{
		List<MusicDirectory.Entry> selectedSongs = getSelectedSongs(albumListView);

		if (selectedSongs.size() > 0)
		{
			download(append, false, !append, false, shuffle, selectedSongs);
			selectAll(false, false);
		}
		else
		{
			playAll(shuffle, append);
		}
	}

	private void playAll()
	{
		playAll(false, false);
	}

	private void playAll(final boolean shuffle, final boolean append)
	{
		boolean hasSubFolders = false;

		for (int i = 0; i < albumListView.getCount(); i++)
		{
			MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(i);
			if (entry != null && entry.isDirectory())
			{
				hasSubFolders = true;
				break;
			}
		}

		boolean isArtist = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_ARTIST, false);
		String id = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID);

		if (hasSubFolders && id != null)
		{
			downloadRecursively(id, false, append, !append, shuffle, false, false, false, isArtist);
		}
		else
		{
			selectAll(true, false);
			download(append, false, !append, false, shuffle, getSelectedSongs(albumListView));
			selectAll(false, false);
		}
	}

	private static List<MusicDirectory.Entry> getSelectedSongs(ListView albumListView)
	{
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(10);

		if (albumListView != null)
		{
			int count = albumListView.getCount();
			for (int i = 0; i < count; i++)
			{
				if (albumListView.isItemChecked(i))
				{
					songs.add((MusicDirectory.Entry) albumListView.getItemAtPosition(i));
				}
			}
		}

		return songs;
	}

	private void refresh()
	{
		finish();
		Intent intent = getIntent();
		intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
		Util.startActivityWithoutTransition(this, intent);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, view, menuInfo);
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

		MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(info.position);

		if (entry != null && entry.isDirectory())
		{
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.select_album_context, menu);
		}

		shareButton = menu.findItem(R.id.menu_item_share);

		if (shareButton != null)
		{
			shareButton.setVisible(!Util.isOffline(this));
		}

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
			return true;
		}

		MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(info.position);

		if (entry == null)
		{
			return true;
		}

		String entryId = entry.getId();

		switch (menuItem.getItemId())
		{
			case R.id.album_menu_play_now:
				downloadRecursively(entryId, false, false, true, false, false, false, false, false);
				break;
			case R.id.album_menu_play_next:
				downloadRecursively(entryId, false, false, false, false, false, true, false, false);
				break;
			case R.id.album_menu_play_last:
				downloadRecursively(entryId, false, true, false, false, false, false, false, false);
				break;
			case R.id.album_menu_pin:
				downloadRecursively(entryId, true, true, false, false, false, false, false, false);
				break;
			case R.id.album_menu_unpin:
				downloadRecursively(entryId, false, false, false, false, false, false, true, false);
				break;
			case R.id.album_menu_download:
				downloadRecursively(entryId, false, false, false, false, true, false, false, false);
				break;
			case R.id.select_album_play_all:
				playAll();
				break;
			case R.id.menu_item_share:
				List<MusicDirectory.Entry> entries = new ArrayList<MusicDirectory.Entry>(1);
				entries.add(entry);
				createShare(entries);
				return true;
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
			case R.id.main_shuffle:
				Intent intent1 = new Intent(this, DownloadActivity.class);
				intent1.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
				Util.startActivityWithoutTransition(this, intent1);
				return true;
			case R.id.select_album_play_all:
				playAll();
				return true;
			case R.id.menu_item_share:
				createShare(getSelectedSongs(albumListView));
				return true;
		}

		return false;
	}

	private void getMusicDirectory(final String id, final String name)
	{
		setActionBarSubtitle(name);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				return service.getMusicDirectory(id, name, refresh, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getArtist(final String id, final String name)
	{
		setActionBarSubtitle(name);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				return service.getArtist(id, name, refresh, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getAlbum(final String id, final String name)
	{
		setActionBarSubtitle(name);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				return service.getAlbum(id, name, refresh, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getSongsForGenre(final String genre, final int count, final int offset)
	{
		setActionBarSubtitle(genre);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return service.getSongsByGenre(genre, count, offset, SelectAlbumActivity.this, this);
			}

			@Override
			protected void done(Pair<MusicDirectory, Boolean> result)
			{
				// Hide more button when results are less than album list size
				if (result.getFirst().getChildren().size() < getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0))
				{
					moreButton.setVisibility(View.GONE);
				}
				else
				{
					moreButton.setVisibility(View.VISIBLE);
				}

				moreButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
						String genre = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_GENRE_NAME);
						int size = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
						int offset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + size;

						intent.putExtra(Constants.INTENT_EXTRA_NAME_GENRE_NAME, genre);
						intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size);
						intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
						Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
					}
				});

				super.done(result);
			}
		}.execute();
	}

	private void getStarred()
	{
		setActionBarSubtitle(R.string.main_songs_starred);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return Util.getShouldUseId3Tags(SelectAlbumActivity.this) ? Util.getSongsFromSearchResult(service.getStarred2(SelectAlbumActivity.this, this)) : Util.getSongsFromSearchResult(service.getStarred(SelectAlbumActivity.this, this));
			}
		}.execute();
	}

	private void getVideos()
	{
		showHeader = false;

		setActionBarSubtitle(R.string.main_videos);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				return service.getVideos(refresh, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getRandom(final int size)
	{
		setActionBarSubtitle(R.string.main_songs_random);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return service.getRandomSongs(size, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getPlaylist(final String playlistId, final String playlistName)
	{
		setActionBarSubtitle(playlistName);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return service.getPlaylist(playlistId, playlistName, SelectAlbumActivity.this, this);
			}
		}.execute();
	}

	private void getShare(final String shareId, final String shareName)
	{
		setActionBarSubtitle(shareName);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				List<Share> shares = service.getShares(true, SelectAlbumActivity.this, this);

				MusicDirectory md = new MusicDirectory();

				for (Share share : shares)
				{
					if (share.getId().equals(shareId))
					{
						for (MusicDirectory.Entry entry : share.getEntries())
						{
							md.addChild(entry);
						}

						break;
					}
				}

				return md;
			}
		}.execute();
	}

	private void getAlbumList(final String albumListType, final int albumListTitle, final int size, final int offset)
	{
		showHeader = false;

		setActionBarSubtitle(albumListTitle);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return Util.getShouldUseId3Tags(SelectAlbumActivity.this) ? service.getAlbumList2(albumListType, size, offset, SelectAlbumActivity.this, this) : service.getAlbumList(albumListType, size, offset, SelectAlbumActivity.this, this);
			}

			@Override
			protected void done(Pair<MusicDirectory, Boolean> result)
			{
				if (!result.getFirst().getChildren().isEmpty())
				{
					pinButton.setVisibility(View.GONE);
					unpinButton.setVisibility(View.GONE);
					downloadButton.setVisibility(View.GONE);
					deleteButton.setVisibility(View.GONE);

					// Hide more button when results are less than album list size
					if (result.getFirst().getChildren().size() < getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0))
					{
						moreButton.setVisibility(View.GONE);
					}
					else
					{
						moreButton.setVisibility(View.VISIBLE);

						moreButton.setOnClickListener(new View.OnClickListener()
						{
							@Override
							public void onClick(View view)
							{
								Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
								int albumListTitle = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0);
								String type = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
								int size = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
								int offset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + size;

								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, albumListTitle);
								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size);
								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
								Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
							}
						});
					}
				}
				else
				{
					moreButton.setVisibility(View.GONE);
				}

				super.done(result);
			}
		}.execute();
	}

	private void selectAllOrNone()
	{
		boolean someUnselected = false;
		int count = albumListView.getCount();

		for (int i = 0; i < count; i++)
		{
			if (!albumListView.isItemChecked(i) && albumListView.getItemAtPosition(i) instanceof MusicDirectory.Entry)
			{
				someUnselected = true;
				break;
			}
		}

		selectAll(someUnselected, true);
	}

	private void selectAll(boolean selected, boolean toast)
	{
		int count = albumListView.getCount();
		int selectedCount = 0;

		for (int i = 0; i < count; i++)
		{
			MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(i);
			if (entry != null && !entry.isDirectory() && !entry.isVideo())
			{
				albumListView.setItemChecked(i, selected);
				selectedCount++;
			}
		}

		// Display toast: N tracks selected / N tracks unselected
		if (toast)
		{
			int toastResId = selected ? R.string.select_album_n_selected : R.string.select_album_n_unselected;
			Util.toast(this, getString(toastResId, selectedCount));
		}

		enableButtons();
	}

	private void enableButtons()
	{
		if (getDownloadService() == null)
		{
			return;
		}

		List<MusicDirectory.Entry> selection = getSelectedSongs(albumListView);
		boolean enabled = !selection.isEmpty();
		boolean unpinEnabled = false;
		boolean deleteEnabled = false;

		int pinnedCount = 0;

		for (MusicDirectory.Entry song : selection)
		{
			DownloadFile downloadFile = getDownloadService().forSong(song);
			if (downloadFile.isWorkDone())
			{
				deleteEnabled = true;
			}

			if (downloadFile.isSaved())
			{
				pinnedCount++;
				unpinEnabled = true;
			}
		}

		playNowButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
		playNextButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
		playLastButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
		pinButton.setVisibility((enabled && !Util.isOffline(this) && selection.size() > pinnedCount) ? View.VISIBLE : View.GONE);
		unpinButton.setVisibility(enabled && unpinEnabled ? View.VISIBLE : View.GONE);
		downloadButton.setVisibility(enabled && !deleteEnabled && !Util.isOffline(this) ? View.VISIBLE : View.GONE);
		deleteButton.setVisibility(enabled && deleteEnabled ? View.VISIBLE : View.GONE);
	}

	private void downloadBackground(final boolean save)
	{
		List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);

		if (songs.isEmpty())
		{
			selectAll(true, false);
			songs = getSelectedSongs(albumListView);
		}

		downloadBackground(save, songs);
	}

	private void downloadBackground(final boolean save, final List<MusicDirectory.Entry> songs)
	{
		if (getDownloadService() == null)
		{
			return;
		}

		Runnable onValid = new Runnable()
		{
			@Override
			public void run()
			{
				warnIfNetworkOrStorageUnavailable();
				getDownloadService().downloadBackground(songs, save);

				if (save)
				{
					Util.toast(SelectAlbumActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
				}
				else
				{
					Util.toast(SelectAlbumActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_downloaded, songs.size(), songs.size()));
				}
			}
		};

		checkLicenseAndTrialPeriod(onValid);
	}

	private void delete()
	{
		List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);

		if (songs.isEmpty())
		{
			selectAll(true, false);
			songs = getSelectedSongs(albumListView);
		}

		if (getDownloadService() != null)
		{
			getDownloadService().delete(songs);
		}
	}

	private void unpin()
	{
		if (getDownloadService() != null)
		{
			List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);
			Util.toast(SelectAlbumActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
			getDownloadService().unpin(songs);
		}
	}

	private abstract class LoadTask extends TabActivityBackgroundTask<Pair<MusicDirectory, Boolean>>
	{

		public LoadTask()
		{
			super(SelectAlbumActivity.this, true);
		}

		protected abstract MusicDirectory load(MusicService service) throws Exception;

		@Override
		protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable
		{
			MusicService musicService = MusicServiceFactory.getMusicService(SelectAlbumActivity.this);
			MusicDirectory dir = load(musicService);
			boolean valid = musicService.isLicenseValid(SelectAlbumActivity.this, this);
			return new Pair<MusicDirectory, Boolean>(dir, valid);
		}

		@Override
		protected void done(Pair<MusicDirectory, Boolean> result)
		{
			MusicDirectory musicDirectory = result.getFirst();
			List<MusicDirectory.Entry> entries = musicDirectory.getChildren();

			if (Util.getShouldSortByDisc(SelectAlbumActivity.this))
			{
				Collections.sort(entries, new EntryByDiscAndTrackComparator());
			}

			String directoryName = musicDirectory.getName();

			boolean allVideos = true;
			int songCount = 0;

			for (MusicDirectory.Entry entry : entries)
			{
				if (!entry.isVideo())
				{
					allVideos = false;
				}

				if (!entry.isDirectory())
				{
					songCount++;
				}
			}

 			final int listSize = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);

			if (songCount > 0)
			{
				if (showHeader)
				{
					View header = createHeader(entries, directoryName, songCount);

					if (header != null)
					{
						albumListView.addHeaderView(header, null, false);
					}
				}

				pinButton.setVisibility(View.VISIBLE);
				unpinButton.setVisibility(View.VISIBLE);
				downloadButton.setVisibility(View.VISIBLE);
				deleteButton.setVisibility(View.VISIBLE);
				selectButton.setVisibility(allVideos ? View.GONE : View.VISIBLE);
				playNowButton.setVisibility(View.VISIBLE);
				playNextButton.setVisibility(View.VISIBLE);
				playLastButton.setVisibility(View.VISIBLE);

				if (listSize == 0 || songCount < listSize)
				{
					moreButton.setVisibility(View.GONE);
				}
				else
				{
					moreButton.setVisibility(View.VISIBLE);

					if (getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_RANDOM, 0) > 0)
					{
						moreButton.setOnClickListener(new View.OnClickListener()
						{
							@Override
							public void onClick(View view)
							{
								Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
								int offset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + listSize;

								intent.putExtra(Constants.INTENT_EXTRA_NAME_RANDOM, 1);
								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, listSize);
								intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
								Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
							}
						});
					}
				}
			}
			else
			{
				pinButton.setVisibility(View.GONE);
				unpinButton.setVisibility(View.GONE);
				downloadButton.setVisibility(View.GONE);
				deleteButton.setVisibility(View.GONE);
				selectButton.setVisibility(View.GONE);
				playNowButton.setVisibility(View.GONE);
				playNextButton.setVisibility(View.GONE);
				playLastButton.setVisibility(View.GONE);

				if (listSize == 0 || result.getFirst().getChildren().size() < listSize)
				{
					albumButtons.setVisibility(View.GONE);
				}
				else
				{
					moreButton.setVisibility(View.VISIBLE);
				}
			}

			enableButtons();

			boolean isAlbumList = getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
			playAllButtonVisible = !(isAlbumList || entries.isEmpty()) && !allVideos;
			shareButtonVisible = !Util.isOffline(SelectAlbumActivity.this) && songCount > 0;

			emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

			if (playAllButton != null)
			{
				playAllButton.setVisible(playAllButtonVisible);
			}

			if (shareButton != null)
			{
				shareButton.setVisible(shareButtonVisible);
			}

			albumListView.setAdapter(new EntryAdapter(SelectAlbumActivity.this, getImageLoader(), entries, true));
			licenseValid = result.getSecond();

			boolean playAll = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);
			if (playAll && songCount > 0)
			{
				playAll(getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, false), false);
			}
		}

		protected View createHeader(List<MusicDirectory.Entry> entries, CharSequence name, int songCount)
		{
			ImageView coverArtView = (ImageView) header.findViewById(R.id.select_album_art);
			int artworkSelection = random.nextInt(entries.size());
			getImageLoader().loadImage(coverArtView, entries.get(artworkSelection), false, Util.getAlbumImageSize(SelectAlbumActivity.this), false, true);

			TextView titleView = (TextView) header.findViewById(R.id.select_album_title);
			titleView.setText(name != null ? name : getActionBarSubtitle());

			AlbumHeader albumHeader = AlbumHeader.processEntries(SelectAlbumActivity.this, entries);

			// Don't show a header if all entries are videos
			if (albumHeader.getIsAllVideo())
			{
				return null;
			}

			TextView artistView = (TextView) header.findViewById(R.id.select_album_artist);
			String artist;

			artist = albumHeader.getArtists().size() == 1 ? albumHeader.getArtists().iterator().next() : albumHeader.getGrandParents().size() == 1 ? albumHeader.getGrandParents().iterator().next() : getResources().getString(R.string.common_various_artists);

			artistView.setText(artist);

			TextView genreView = (TextView) header.findViewById(R.id.select_album_genre);
			String genre;

			genre = albumHeader.getGenres().size() == 1 ? albumHeader.getGenres().iterator().next() : getResources().getString(R.string.common_multiple_genres);

			genreView.setText(genre);

			TextView songCountView = (TextView) header.findViewById(R.id.select_album_song_count);
			String songs = getResources().getQuantityString(R.plurals.select_album_n_songs, songCount, songCount);
			songCountView.setText(songs);

			String duration = Util.formatTotalDuration(albumHeader.getTotalDuration());

			TextView durationView = (TextView) header.findViewById(R.id.select_album_duration);
			durationView.setText(duration);

			return header;
		}
	}

	private class GetDataTask extends AsyncTask<Void, Void, String[]>
	{
		@Override
		protected void onPostExecute(String[] result)
		{
			refreshAlbumListView.onRefreshComplete();
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
