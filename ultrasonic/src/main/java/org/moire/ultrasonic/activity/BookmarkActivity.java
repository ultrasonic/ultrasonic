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
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Pair;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.EntryAdapter;

import java.util.ArrayList;
import java.util.List;

public class BookmarkActivity extends SubsonicTabActivity
{

	private PullToRefreshListView refreshAlbumListView;
	private ListView albumListView;
	private View albumButtons;
	private View emptyView;
	private ImageView playNowButton;
	private ImageView pinButton;
	private ImageView unpinButton;
	private ImageView downloadButton;
	private ImageView deleteButton;

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

		albumListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		albumListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				if (position >= 0)
				{
					Entry entry = (Entry) parent.getItemAtPosition(position);

					if (entry != null)
					{
						if (entry.isVideo())
						{
							playVideo(entry);
						}
						else
						{
							enableButtons();
						}
					}
				}
			}
		});

		ImageView selectButton = (ImageView) findViewById(R.id.select_album_select);
		playNowButton = (ImageView) findViewById(R.id.select_album_play_now);
		ImageView playNextButton = (ImageView) findViewById(R.id.select_album_play_next);
		ImageView playLastButton = (ImageView) findViewById(R.id.select_album_play_last);
		pinButton = (ImageView) findViewById(R.id.select_album_pin);
		unpinButton = (ImageView) findViewById(R.id.select_album_unpin);
		downloadButton = (ImageView) findViewById(R.id.select_album_download);
		deleteButton = (ImageView) findViewById(R.id.select_album_delete);
		ImageView oreButton = (ImageView) findViewById(R.id.select_album_more);
		emptyView = findViewById(R.id.select_album_empty);

		selectButton.setVisibility(View.GONE);
		playNextButton.setVisibility(View.GONE);
		playLastButton.setVisibility(View.GONE);
		oreButton.setVisibility(View.GONE);

		playNowButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				playNow(getSelectedSongs(albumListView));
			}
		});

		selectButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				selectAllOrNone();
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

		View browseMenuItem = findViewById(R.id.menu_bookmarks);
		menuDrawer.setActiveView(browseMenuItem);

		getBookmarks();
	}

	private void getBookmarks()
	{
		setActionBarSubtitle(R.string.button_bar_bookmarks);

		new LoadTask()
		{
			@Override
			protected MusicDirectory load(MusicService service) throws Exception
			{
				return Util.getSongsFromBookmarks(service.getBookmarks(BookmarkActivity.this, this));
			}
		}.execute();
	}

	private void playNow(List<Entry> songs)
	{
		if (!getSelectedSongs(albumListView).isEmpty())
		{
			int position = songs.get(0).getBookmarkPosition();
			if (getMediaPlayerController() == null) return;
			getMediaPlayerController().restore(songs, 0, position, true, true);
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
		startActivityForResultWithoutTransition(this, intent);
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
		MediaPlayerController mediaPlayerController = getMediaPlayerController();
		if (mediaPlayerController == null)
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
			DownloadFile downloadFile = mediaPlayerController.getDownloadFileForSong(song);
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

		playNowButton.setVisibility(enabled && deleteEnabled ? View.VISIBLE : View.GONE);
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
		if (getMediaPlayerController() == null)
		{
			return;
		}

		Runnable onValid = new Runnable()
		{
			@Override
			public void run()
			{
				warnIfNetworkOrStorageUnavailable();
				getMediaPlayerController().downloadBackground(songs, save);

				if (save)
				{
					Util.toast(BookmarkActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
				}
				else
				{
					Util.toast(BookmarkActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_downloaded, songs.size(), songs.size()));
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

		if (getMediaPlayerController() != null)
		{
			getMediaPlayerController().delete(songs);
		}
	}

	private void unpin()
	{
		if (getMediaPlayerController() != null)
		{
			List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);
			Util.toast(BookmarkActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
			getMediaPlayerController().unpin(songs);
		}
	}

	private abstract class LoadTask extends TabActivityBackgroundTask<Pair<MusicDirectory, Boolean>>
	{

		public LoadTask()
		{
			super(BookmarkActivity.this, true);
		}

		protected abstract MusicDirectory load(MusicService service) throws Exception;

		@Override
		protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable
		{
			MusicService musicService = MusicServiceFactory.getMusicService(BookmarkActivity.this);
			MusicDirectory dir = load(musicService);
			boolean valid = musicService.isLicenseValid(BookmarkActivity.this, this);
			return new Pair<MusicDirectory, Boolean>(dir, valid);
		}

		@Override
		protected void done(Pair<MusicDirectory, Boolean> result)
		{
			MusicDirectory musicDirectory = result.getFirst();
			List<MusicDirectory.Entry> entries = musicDirectory.getChildren();

			int songCount = 0;
			for (MusicDirectory.Entry entry : entries)
			{
				if (!entry.isDirectory())
				{
					songCount++;
				}
			}

			final int listSize = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);

			if (songCount > 0)
			{
				pinButton.setVisibility(View.VISIBLE);
				unpinButton.setVisibility(View.VISIBLE);
				downloadButton.setVisibility(View.VISIBLE);
				deleteButton.setVisibility(View.VISIBLE);
				playNowButton.setVisibility(View.VISIBLE);
			}
			else
			{
				pinButton.setVisibility(View.GONE);
				unpinButton.setVisibility(View.GONE);
				downloadButton.setVisibility(View.GONE);
				deleteButton.setVisibility(View.GONE);
				playNowButton.setVisibility(View.GONE);

				if (listSize == 0 || result.getFirst().getChildren().size() < listSize)
				{
					albumButtons.setVisibility(View.GONE);
				}
			}

			enableButtons();

			emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

			albumListView.setAdapter(new EntryAdapter(BookmarkActivity.this, getImageLoader(), entries, true));
			licenseValid = result.getSecond();
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
