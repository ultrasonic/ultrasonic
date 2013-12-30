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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.service.OfflineException;
import com.thejoshwa.ultrasonic.androidapp.service.ServerTooOldException;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.LoadingTask;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.TimeSpan;
import com.thejoshwa.ultrasonic.androidapp.util.TimeSpanPicker;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.view.ShareAdapter;

import java.util.List;

public class ShareActivity extends SubsonicTabActivity implements AdapterView.OnItemClickListener
{

	private PullToRefreshListView refreshSharesListView;
	private ListView sharesListView;
	private View emptyTextView;
	private ShareAdapter shareAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.select_share);

		refreshSharesListView = (PullToRefreshListView) findViewById(R.id.select_share_list);
		sharesListView = refreshSharesListView.getRefreshableView();

		refreshSharesListView.setOnRefreshListener(new OnRefreshListener<ListView>()
		{
			@Override
			public void onRefresh(PullToRefreshBase<ListView> refreshView)
			{
				new GetDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		});

		emptyTextView = findViewById(R.id.select_share_empty);
		sharesListView.setOnItemClickListener(this);
		registerForContextMenu(sharesListView);

		View sharesMenuItem = findViewById(R.id.menu_shares);
		menuDrawer.setActiveView(sharesMenuItem);

		setActionBarTitle(R.string.common_appname);
		setActionBarSubtitle(R.string.button_bar_shares);

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
		Intent intent = new Intent(this, ShareActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
		Util.startActivityWithoutTransition(this, intent);
	}

	private void load()
	{
		BackgroundTask<List<Share>> task = new TabActivityBackgroundTask<List<Share>>(this, true)
		{
			@Override
			protected List<Share> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(ShareActivity.this);
				boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
				return musicService.getShares(refresh, ShareActivity.this, this);
			}

			@Override
			protected void done(List<Share> result)
			{
				sharesListView.setAdapter(shareAdapter = new ShareAdapter(ShareActivity.this, result));
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
		inflater.inflate(R.menu.select_share_context, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuItem)
	{
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
		if (info == null)
		{
			return false;
		}

		Share share = (Share) sharesListView.getItemAtPosition(info.position);
		if (share == null)
		{
			return false;
		}

		switch (menuItem.getItemId())
		{
			case R.id.share_menu_pin:
				downloadShare(share.getId(), share.getName(), true, true, false, false, true, false, false);
				break;
			case R.id.share_menu_unpin:
				downloadShare(share.getId(), share.getName(), false, false, false, false, true, false, true);
				break;
			case R.id.share_menu_download:
				downloadShare(share.getId(), share.getName(), false, false, false, false, true, false, false);
				break;
			case R.id.share_menu_play_now:
				downloadShare(share.getId(), share.getName(), false, false, true, false, false, false, false);
				break;
			case R.id.share_menu_play_shuffled:
				downloadShare(share.getId(), share.getName(), false, false, true, true, false, false, false);
				break;
			case R.id.share_menu_delete:
				deleteShare(share);
				break;
			case R.id.share_info:
				displayShareInfo(share);
				break;
			case R.id.share_update_info:
				updateShareInfo(share);
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
		Share share = (Share) parent.getItemAtPosition(position);

		if (share == null)
		{
			return;
		}

		Intent intent = new Intent(ShareActivity.this, SelectAlbumActivity.class);
		intent.putExtra(Constants.INTENT_EXTRA_NAME_SHARE_ID, share.getId());
		intent.putExtra(Constants.INTENT_EXTRA_NAME_SHARE_NAME, share.getName());
		Util.startActivityWithoutTransition(ShareActivity.this, intent);
	}

	private void deleteShare(final Share share)
	{
		new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.common_confirm).setMessage(getResources().getString(R.string.delete_playlist, share.getName())).setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				new LoadingTask<Void>(ShareActivity.this, false)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						MusicService musicService = MusicServiceFactory.getMusicService(ShareActivity.this);
						musicService.deleteShare(share.getId(), ShareActivity.this, null);
						return null;
					}

					@Override
					protected void done(Void result)
					{
						shareAdapter.remove(share);
						shareAdapter.notifyDataSetChanged();
						Util.toast(ShareActivity.this, getResources().getString(R.string.menu_deleted_share, share.getName()));
					}

					@Override
					protected void error(Throwable error)
					{
						String msg;
						msg = error instanceof OfflineException || error instanceof ServerTooOldException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.menu_deleted_share_error, share.getName()), getErrorMessage(error));

						Util.toast(ShareActivity.this, msg, false);
					}
				}.execute();
			}

		}).setNegativeButton(R.string.common_cancel, null).show();
	}

	private void displayShareInfo(final Share share)
	{
		final TextView textView = new TextView(this);
		textView.setPadding(5, 5, 5, 5);

		final Spannable message = new SpannableString("Owner: " + share.getUsername() +
				"\nComments: " + ((share.getDescription() == null) ? "" : share.getDescription()) +
				"\nURL: " + share.getUrl() +
				"\nEntry Count: " + share.getEntries().size() +
				"\nVisit Count: " + share.getVisitCount() +
				((share.getCreated() == null) ? "" : ("\nCreation Date: " + share.getCreated().replace('T', ' '))) +
				((share.getLastVisited() == null) ? "" : ("\nLast Visited Date: " + share.getLastVisited().replace('T', ' '))) +
				((share.getExpires() == null) ? "" : ("\nExpiration Date: " + share.getExpires().replace('T', ' '))));

		Linkify.addLinks(message, Linkify.WEB_URLS);
		textView.setText(message);
		textView.setMovementMethod(LinkMovementMethod.getInstance());

		new AlertDialog.Builder(this).setTitle("Share Details").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setView(textView).show();
	}

	private void updateShareInfo(final Share share)
	{
		View dialogView = getLayoutInflater().inflate(R.layout.share_details, null);
		if (dialogView == null)
		{
			return;
		}

		final EditText shareDescription = (EditText) dialogView.findViewById(R.id.share_description);
		final TimeSpanPicker timeSpanPicker = (TimeSpanPicker) dialogView.findViewById(R.id.date_picker);

		shareDescription.setText(share.getDescription());

		CheckBox hideDialogCheckBox = (CheckBox) dialogView.findViewById(R.id.hide_dialog);
		CheckBox saveAsDefaultsCheckBox = (CheckBox) dialogView.findViewById(R.id.save_as_defaults);
		CheckBox noExpirationCheckBox = (CheckBox) dialogView.findViewById(R.id.timeSpanDisableCheckBox);

		noExpirationCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(CompoundButton compoundButton, boolean b)
			{
				timeSpanPicker.setEnabled(!b);
			}
		});

		noExpirationCheckBox.setChecked(true);

		timeSpanPicker.setTimeSpanDisableText(getResources().getText(R.string.no_expiration));

		hideDialogCheckBox.setVisibility(View.GONE);
		saveAsDefaultsCheckBox.setVisibility(View.GONE);

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

		alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
		alertDialog.setTitle(R.string.playlist_update_info);
		alertDialog.setView(dialogView);
		alertDialog.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				new LoadingTask<Void>(ShareActivity.this, false)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						long millis = timeSpanPicker.getTimeSpan().getTotalMilliseconds();

						if (millis > 0)
						{
							millis = TimeSpan.getCurrentTime().add(millis).getTotalMilliseconds();
						}

						Editable shareDescriptionText = shareDescription.getText();
						String description = shareDescriptionText != null ? shareDescriptionText.toString() : null;

						MusicService musicService = MusicServiceFactory.getMusicService(ShareActivity.this);
						musicService.updateShare(share.getId(), description, millis, ShareActivity.this, null);
						return null;
					}

					@Override
					protected void done(Void result)
					{
						refresh();
						Util.toast(ShareActivity.this, getResources().getString(R.string.playlist_updated_info, share.getName()));
					}

					@Override
					protected void error(Throwable error)
					{
						String msg;
						msg = error instanceof OfflineException || error instanceof ServerTooOldException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.playlist_updated_info_error, share.getName()), getErrorMessage(error));

						Util.toast(ShareActivity.this, msg, false);
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
			refreshSharesListView.onRefreshComplete();
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