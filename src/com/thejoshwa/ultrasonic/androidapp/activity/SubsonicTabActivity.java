/*
 This file is part of UltraSonic.

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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.EntryByDiscAndTrackComparator;
import com.thejoshwa.ultrasonic.androidapp.util.ImageLoader;
import com.thejoshwa.ultrasonic.androidapp.util.ModalBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.ShareDetails;
import com.thejoshwa.ultrasonic.androidapp.util.SilentBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.util.VideoPlayerType;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class SubsonicTabActivity extends Activity implements OnClickListener
{
	private static final String TAG = SubsonicTabActivity.class.getSimpleName();
	private static ImageLoader IMAGE_LOADER;
	protected static String theme;
	private static SubsonicTabActivity instance;

	private boolean destroyed;

	private static final String STATE_MENUDRAWER = "com.thejoshwa.ultrasonic.androidapp.menuDrawer";
	private static final String STATE_ACTIVE_VIEW_ID = "com.thejoshwa.ultrasonic.androidapp.activeViewId";
	private static final String STATE_ACTIVE_POSITION = "com.thejoshwa.ultrasonic.androidapp.activePosition";
	private static final int DIALOG_ASK_FOR_SHARE_DETAILS = 102;

	public MenuDrawer menuDrawer;
	private int activePosition = 1;
	private int menuActiveViewId;
	private View nowPlayingView;
	View chatMenuItem;
	View bookmarksMenuItem;
	public static boolean nowPlayingHidden;
	public static Entry currentSong;
	public Bitmap nowPlayingImage;
	boolean licenseValid;
	NotificationManager notificationManager;
	private EditText shareDescription;
	DatePicker datePicker;
	CheckBox hideDialogCheckBox;
	CheckBox noExpirationCheckBox;
	ShareDetails shareDetails;

	@Override
	protected void onCreate(Bundle bundle)
	{
		setUncaughtExceptionHandler();
		applyTheme();
		super.onCreate(bundle);

		startService(new Intent(this, DownloadServiceImpl.class));
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		if (bundle != null)
		{
			activePosition = bundle.getInt(STATE_ACTIVE_POSITION);
			menuActiveViewId = bundle.getInt(STATE_ACTIVE_VIEW_ID);
		}

		menuDrawer = MenuDrawer.attach(this, MenuDrawer.Type.BEHIND, Position.LEFT, MenuDrawer.MENU_DRAG_WINDOW);
		menuDrawer.setMenuView(R.layout.menu_main);

		chatMenuItem = findViewById(R.id.menu_chat);
		bookmarksMenuItem = findViewById(R.id.menu_bookmarks);

		findViewById(R.id.menu_home).setOnClickListener(this);
		findViewById(R.id.menu_browse).setOnClickListener(this);
		findViewById(R.id.menu_search).setOnClickListener(this);
		findViewById(R.id.menu_playlists).setOnClickListener(this);
		chatMenuItem.setOnClickListener(this);
		bookmarksMenuItem.setOnClickListener(this);
		findViewById(R.id.menu_now_playing).setOnClickListener(this);
		findViewById(R.id.menu_settings).setOnClickListener(this);
		findViewById(R.id.menu_about).setOnClickListener(this);
		findViewById(R.id.menu_exit).setOnClickListener(this);
		setActionBarDisplayHomeAsUp(true);

		TextView activeView = (TextView) findViewById(menuActiveViewId);

		if (activeView != null)
		{
			menuDrawer.setActiveView(activeView);
		}

		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	protected void onPostCreate(Bundle bundle)
	{
		super.onPostCreate(bundle);
		instance = this;

		int visibility = Util.isOffline(this) ? View.GONE : View.VISIBLE;
		chatMenuItem.setVisibility(visibility);
		bookmarksMenuItem.setVisibility(visibility);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		applyTheme();
		instance = this;

		Util.registerMediaButtonEventReceiver(this);

		// Make sure to update theme
		if (theme != null && !theme.equals(Util.getTheme(this)))
		{
			theme = Util.getTheme(this);
			restart();
		}

		if (!nowPlayingHidden)
		{
			showNowPlaying();
		}
		else
		{
			hideNowPlaying();
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
	protected void onDestroy()
	{
		Util.unregisterMediaButtonEventReceiver(this);
		super.onDestroy();
		destroyed = true;
		nowPlayingView = null;
		getImageLoader().clear();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		boolean isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
		boolean isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
		boolean isVolumeAdjust = isVolumeDown || isVolumeUp;
		boolean isJukebox = getDownloadService() != null && getDownloadService().isJukeboxEnabled();

		if (isVolumeAdjust && isJukebox)
		{
			getDownloadService().adjustJukeboxVolume(isVolumeUp);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	protected void restart()
	{
		Intent intent = new Intent(this, this.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtras(getIntent());
		Util.startActivityWithoutTransition(this, intent);
	}

	@Override
	public void finish()
	{
		super.finish();
		Util.disablePendingTransition(this);
	}

	@Override
	public boolean isDestroyed()
	{
		return destroyed;
	}

	public void showNowPlaying()
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				new SilentBackgroundTask<Void>(SubsonicTabActivity.this)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						if (!Util.getShowNowPlayingPreference(SubsonicTabActivity.this))
						{
							hideNowPlaying();
							return null;
						}

						nowPlayingView = findViewById(R.id.now_playing);

						if (nowPlayingView != null)
						{
							final DownloadService downloadService = DownloadServiceImpl.getInstance();

							if (downloadService != null)
							{
								PlayerState playerState = downloadService.getPlayerState();

								if (playerState.equals(PlayerState.PAUSED) || playerState.equals(PlayerState.STARTED))
								{
									DownloadFile file = downloadService.getCurrentPlaying();

									if (file != null)
									{
										final Entry song = file.getSong();
										showNowPlaying(SubsonicTabActivity.this, downloadService, song, playerState);
									}
								}
								else
								{
									hideNowPlaying();
								}
							}
						}

						return null;
					}

					@Override
					protected void done(Void result)
					{
					}
				}.execute();
			}
		});
	}

	private void applyTheme()
	{
		String theme = Util.getTheme(this);

		if ("dark".equalsIgnoreCase(theme) || "fullscreen".equalsIgnoreCase(theme))
		{
			setTheme(R.style.UltraSonicTheme);
		}
		else if ("light".equalsIgnoreCase(theme) || "fullscreenlight".equalsIgnoreCase(theme))
		{
			setTheme(R.style.UltraSonicTheme_Light);
		}
	}

	public void showNotification(final Handler handler, final MusicDirectory.Entry song, final DownloadServiceImpl downloadService, final Notification notification, final PlayerState playerState)
	{
		if (!Util.isNotificationEnabled(this))
		{
			return;
		}

		new AsyncTask<Void, Void, String[]>()
		{
			@Override
			protected void onPostExecute(String[] result)
			{
				super.onPostExecute(result);
			}

			@SuppressLint("NewApi")
			@Override
			protected String[] doInBackground(Void... params)
			{
				RemoteViews notificationView = notification.contentView;
				RemoteViews bigNotificationView = null;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				{
					bigNotificationView = notification.bigContentView;
				}

				if (playerState == PlayerState.PAUSED)
				{
					setImageViewResourceOnUiThread(notificationView, R.id.control_play, R.drawable.media_start_normal_dark);

					if (bigNotificationView != null)
					{
						setImageViewResourceOnUiThread(bigNotificationView, R.id.control_play, R.drawable.media_start_normal_dark);
					}
				}
				else if (playerState == PlayerState.STARTED)
				{
					setImageViewResourceOnUiThread(notificationView, R.id.control_play, R.drawable.media_pause_normal_dark);

					if (bigNotificationView != null)
					{
						setImageViewResourceOnUiThread(bigNotificationView, R.id.control_play, R.drawable.media_pause_normal_dark);
					}
				}

				if (currentSong != song)
				{
					currentSong = song;

					String title = song.getTitle();
					String text = song.getArtist();
					String album = song.getAlbum();

					try
					{
						if (nowPlayingImage == null)
						{
							setImageViewResourceOnUiThread(notificationView, R.id.notification_image, R.drawable.unknown_album);

							if (bigNotificationView != null)
							{
								setImageViewResourceOnUiThread(bigNotificationView, R.id.notification_image, R.drawable.unknown_album);
							}
						}
						else
						{
							setImageViewBitmapOnUiThread(notificationView, R.id.notification_image, nowPlayingImage);

							if (bigNotificationView != null)
							{
								setImageViewBitmapOnUiThread(bigNotificationView, R.id.notification_image, nowPlayingImage);
							}
						}
					}
					catch (Exception x)
					{
						Log.w(TAG, "Failed to get notification cover art", x);
						setImageViewResourceOnUiThread(notificationView, R.id.notification_image, R.drawable.unknown_album);

						if (bigNotificationView != null)
						{
							setImageViewResourceOnUiThread(bigNotificationView, R.id.notification_image, R.drawable.unknown_album);
						}
					}

					setTextViewTextOnUiThread(notificationView, R.id.trackname, title);
					setTextViewTextOnUiThread(notificationView, R.id.artist, text);
					setTextViewTextOnUiThread(notificationView, R.id.album, album);

					if (bigNotificationView != null)
					{
						setTextViewTextOnUiThread(bigNotificationView, R.id.trackname, title);
						setTextViewTextOnUiThread(bigNotificationView, R.id.artist, text);
						setTextViewTextOnUiThread(bigNotificationView, R.id.album, album);
					}
				}

				// Send the notification and put the service in the foreground.
				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						downloadService.startForeground(Constants.NOTIFICATION_ID_PLAYING, notification);
					}
				});

				return null;
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void hidePlayingNotification(final Handler handler, final DownloadServiceImpl downloadService) {

		currentSong = null;

		// Remove notification and remove the service from the foreground
		handler.post(new Runnable(){
			@Override
			public void run() {
				downloadService.stopForeground(true);
			}
		});
	}

	private void showNowPlaying(final Context context, final DownloadService downloadService, final MusicDirectory.Entry song, final PlayerState playerState)
	{
		if (context == null || downloadService == null || song == null || playerState == null)
		{
			return;
		}

		if (!Util.getShowNowPlayingPreference(context))
		{
			hideNowPlaying();
			return;
		}

		if (nowPlayingView == null)
		{
			nowPlayingView = findViewById(R.id.now_playing);
		}

		if (nowPlayingView != null)
		{
			try
			{
				setVisibilityOnUiThread(nowPlayingView, View.VISIBLE);
				nowPlayingHidden = false;

				ImageView playButton = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);

				if (playerState == PlayerState.PAUSED)
				{
					setImageDrawableOnUiThread(playButton, Util.getDrawableFromAttribute(context, R.attr.media_play));
				}
				else if (playerState == PlayerState.STARTED)
				{
					setImageDrawableOnUiThread(playButton, Util.getDrawableFromAttribute(context, R.attr.media_pause));
				}

				String title = song.getTitle();
				String artist = song.getArtist();

				final ImageView nowPlayingAlbumArtImage = (ImageView) nowPlayingView.findViewById(R.id.now_playing_image);
				TextView nowPlayingTrack = (TextView) nowPlayingView.findViewById(R.id.now_playing_trackname);
				TextView nowPlayingArtist = (TextView) nowPlayingView.findViewById(R.id.now_playing_artist);

				this.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						getImageLoader().loadImage(nowPlayingAlbumArtImage, song, false, Util.getNotificationImageSize(context), false, true);
					}
				});

				final Intent intent = new Intent(context, SelectAlbumActivity.class);

				if (Util.getShouldUseId3Tags(context))
				{
					intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, true);
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getAlbumId());
				}
				else
				{
					intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false);
					intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getParent());
				}

				intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, song.getAlbum());

				setOnClickListenerOnUiThread(nowPlayingAlbumArtImage, new View.OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
					}
				});

				setTextOnUiThread(nowPlayingTrack, title);
				setTextOnUiThread(nowPlayingArtist, artist);

				ImageView nowPlayingControlPlay = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);

				SwipeDetector swipeDetector = SwipeDetector.Create(SubsonicTabActivity.this, downloadService);
				setOnTouchListenerOnUiThread(nowPlayingView, swipeDetector);

				setOnClickListenerOnUiThread(nowPlayingView, new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
					}
				});

				setOnClickListenerOnUiThread(nowPlayingControlPlay, new View.OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						downloadService.togglePlayPause();
					}
				});

			}
			catch (Exception x)
			{
				Log.w(TAG, "Failed to get notification cover art", x);
			}
		}
	}

	public void hideNowPlaying()
	{
		try
		{
			if (nowPlayingView == null)
			{
				nowPlayingView = findViewById(R.id.now_playing);
			}

			if (nowPlayingView != null)
			{
				setVisibilityOnUiThread(nowPlayingView, View.GONE);
			}
		}
		catch (Exception ex)
		{
			Log.w(String.format("Exception in hideNowPlaying: %s", ex), ex);
		}
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_ASK_FOR_SHARE_DETAILS)
		{
			final LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			final View layout = layoutInflater.inflate(R.layout.share_details, (ViewGroup) findViewById(R.id.share_details));

			if (layout != null)
			{
				shareDescription = (EditText) layout.findViewById(R.id.share_description);
				hideDialogCheckBox = (CheckBox) layout.findViewById(R.id.hide_dialog);
				noExpirationCheckBox = (CheckBox) layout.findViewById(R.id.no_expiration);
				datePicker = (DatePicker) layout.findViewById(R.id.date_picker);
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.share_set_share_options);
			builder.setMessage(R.string.share_description);
			builder.setPositiveButton(R.string.common_save, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int clickId)
				{
					if (!noExpirationCheckBox.isChecked())
					{
						Calendar cal = Calendar.getInstance();
						cal.setTime(Util.getDateFromDatePicker(datePicker));
						shareDetails.Expiration = cal;
					}

					shareDetails.Description = String.valueOf(shareDescription.getText());

					if (hideDialogCheckBox.isChecked())
					{
						Util.setShouldAskForShareDetails(SubsonicTabActivity.this, false);
					}

					share();
				}
			});
			builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int clickId)
				{
					shareDetails = null;
					dialog.cancel();
				}
			});
			builder.setView(layout);
			builder.setCancelable(true);

			noExpirationCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
			{
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b)
				{
					datePicker.setEnabled(!b);
				}
			});

			return builder.create();
		}
		else
		{
			return super.onCreateDialog(id);
		}
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_ASK_FOR_SHARE_DETAILS)
		{
			String defaultDescription = Util.getDefaultShareDescription(this);
			Calendar cal = Util.getDefaultShareExpirationCalendar(this);

			if (cal != null) {
				noExpirationCheckBox.setChecked(false);
				datePicker.setEnabled(true);
				datePicker.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), null);
			}
			else
			{
				noExpirationCheckBox.setChecked(true);
				datePicker.setEnabled(false);
			};

			shareDescription.setText(defaultDescription);
		}
	}

	public void createShare(final List<MusicDirectory.Entry> entries)
	{
		boolean askForDetails = Util.getShouldAskForShareDetails(this);

		shareDetails = new ShareDetails();
		shareDetails.Entries = entries;

		if (askForDetails)
		{
			showDialog(DIALOG_ASK_FOR_SHARE_DETAILS);
		}
		else
		{
			shareDetails.Description = Util.getDefaultShareDescription(this);
			shareDetails.Expiration = Util.getDefaultShareExpirationCalendar(this);
			share();
		}
	}

	public void share()
	{
		BackgroundTask<Share> task = new TabActivityBackgroundTask<Share>(this, true)
		{
			@Override
			protected Share doInBackground() throws Throwable
			{
				List<String> ids = new ArrayList<String>();

				if (shareDetails.Entries.isEmpty())
				{
					ids.add(getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID));
				}
				else
				{
					for (MusicDirectory.Entry entry : shareDetails.Entries)
					{
						ids.add(entry.getId());
					}
				}

				MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);

				long timeInMillis = 0;

				if (shareDetails.Expiration != null)
					timeInMillis = shareDetails.Expiration.getTimeInMillis();

				List<Share> shares = musicService.createShare(ids, shareDetails.Description, timeInMillis, SubsonicTabActivity.this, this);
				return shares.get(0);
			}

			@Override
			protected void done(Share result)
			{
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, String.format("Check out this music I shared from UltraSonic\n\n%s", result.getUrl()));
				startActivity(Intent.createChooser(intent, "Share via"));
			}
		};

		task.execute();
	}

	public void setTextViewTextOnUiThread(final RemoteViews view, final int id, final String text)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setTextViewText(id, text);
				}
			}
		});
	}

	public void setImageViewBitmapOnUiThread(final RemoteViews view, final int id, final Bitmap bitmap)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setImageViewBitmap(id, bitmap);
				}
			}
		});
	}

	public void setImageViewResourceOnUiThread(final RemoteViews view, final int id, final int resouce)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null)
				{
					view.setImageViewResource(id, resouce);
				}
			}
		});
	}

	public void setOnTouchListenerOnUiThread(final View view, final View.OnTouchListener listener)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setOnTouchListener(listener);
				}
			}
		});
	}

	public void setOnClickListenerOnUiThread(final View view, final View.OnClickListener listener)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setOnClickListener(listener);
				}
			}
		});
	}

	public void setTextOnUiThread(final TextView view, final CharSequence text)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setText(text);
				}
			}
		});
	}

	public void setImageDrawableOnUiThread(final ImageView view, final Drawable drawable)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != View.GONE)
				{
					view.setImageDrawable(drawable);
				}
			}
		});
	}

	public void setVisibilityOnUiThread(final View view, final int visibility)
	{
		this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (view != null && view.getVisibility() != visibility)
				{
					view.setVisibility(visibility);
				}
			}
		});
	}

	public static SubsonicTabActivity getInstance()
	{
		return instance;
	}

	public boolean getIsDestroyed()
	{
		return destroyed;
	}

	public void setProgressVisible(boolean visible)
	{
		View view = findViewById(R.id.tab_progress);
		if (view != null)
		{
			view.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	public void updateProgress(CharSequence message)
	{
		TextView view = (TextView) findViewById(R.id.tab_progress_message);
		if (view != null)
		{
			view.setText(message);
		}
	}

	public DownloadService getDownloadService()
	{
		// If service is not available, request it to start and wait for it.
		for (int i = 0; i < 5; i++)
		{
			DownloadService downloadService = DownloadServiceImpl.getInstance();

			if (downloadService != null)
			{
				return downloadService;
			}

			Log.w(TAG, "DownloadService not running. Attempting to start it.");
			startService(new Intent(this, DownloadServiceImpl.class));
			Util.sleepQuietly(50L);
		}

		return DownloadServiceImpl.getInstance();
	}

	protected void warnIfNetworkOrStorageUnavailable()
	{
		if (!Util.isExternalStoragePresent())
		{
			Util.toast(this, R.string.select_album_no_sdcard);
		}
		else if (!Util.isOffline(this) && !Util.isNetworkConnected(this))
		{
			Util.toast(this, R.string.select_album_no_network);
		}
	}

	protected synchronized ImageLoader getImageLoader()
	{
		if (IMAGE_LOADER == null)
		{
			IMAGE_LOADER = new ImageLoader(this);
		}
		return IMAGE_LOADER;
	}

	void download(final boolean append, final boolean save, final boolean autoPlay, final boolean playNext, final boolean shuffle, final List<Entry> songs)
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
				if (!append && !playNext)
				{
					getDownloadService().clear();
				}

				warnIfNetworkOrStorageUnavailable();
				getDownloadService().download(songs, save, autoPlay, playNext, shuffle, false);
				String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);

				if (playlistName != null)
				{
					getDownloadService().setSuggestedPlaylistName(playlistName);
				}

				if (autoPlay)
				{
					if (Util.getShouldTransitionOnPlaybackPreference(SubsonicTabActivity.this))
					{
						Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
					}
				}
				else if (save)
				{
					Util.toast(SubsonicTabActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
				}
				else if (playNext)
				{
					Util.toast(SubsonicTabActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_play_next, songs.size(), songs.size()));
				}
				else if (append)
				{
					Util.toast(SubsonicTabActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_added, songs.size(), songs.size()));
				}
			}
		};

		checkLicenseAndTrialPeriod(onValid);
	}

	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext, final boolean unpin, final boolean isArtist)
	{
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext, unpin, isArtist);
	}

	protected void downloadPlaylist(final String id, final String name, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext, final boolean unpin)
	{
		downloadRecursively(id, name, false, save, append, autoplay, shuffle, background, playNext, unpin, false);
	}

	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext, final boolean unpin, final boolean isArtist)
	{
		ModalBackgroundTask<List<MusicDirectory.Entry>> task = new ModalBackgroundTask<List<MusicDirectory.Entry>>(this, false)
		{
			private static final int MAX_SONGS = 500;

			@Override
			protected List<MusicDirectory.Entry> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
				List<MusicDirectory.Entry> songs = new LinkedList<MusicDirectory.Entry>();
				MusicDirectory root;

				if (!Util.isOffline(SubsonicTabActivity.this) && isArtist && Util.getShouldUseId3Tags(SubsonicTabActivity.this))
				{
					getSongsForArtist(id, songs);
				}
				else
				{
					if (isDirectory)
					{
						root = !Util.isOffline(SubsonicTabActivity.this) && Util.getShouldUseId3Tags(SubsonicTabActivity.this) ? musicService.getAlbum(id, name, false, SubsonicTabActivity.this, this) : musicService.getMusicDirectory(id, name, false, SubsonicTabActivity.this, this);
					}
					else
					{
						root = musicService.getPlaylist(id, name, SubsonicTabActivity.this, this);
					}

					getSongsRecursively(root, songs);
				}

				return songs;
			}

			private void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs) throws Exception
			{
				if (songs.size() > MAX_SONGS)
				{
					return;
				}

				for (MusicDirectory.Entry song : parent.getChildren(false, true))
				{
					if (!song.isVideo())
					{
						songs.add(song);
					}
				}

				MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);

				for (MusicDirectory.Entry dir : parent.getChildren(true, false))
				{
					MusicDirectory root;

					root = !Util.isOffline(SubsonicTabActivity.this) && Util.getShouldUseId3Tags(SubsonicTabActivity.this) ? musicService.getAlbum(dir.getId(), dir.getTitle(), false, SubsonicTabActivity.this, this) : musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, SubsonicTabActivity.this, this);

					getSongsRecursively(root, songs);
				}
			}

			private void getSongsForArtist(String id, Collection<Entry> songs) throws Exception
			{
				if (songs.size() > MAX_SONGS)
				{
					return;
				}

				MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
				MusicDirectory artist = musicService.getArtist(id, "", false, SubsonicTabActivity.this, this);

				for (MusicDirectory.Entry album : artist.getChildren())
				{
					MusicDirectory albumDirectory = musicService.getAlbum(album.getId(), "", false, SubsonicTabActivity.this, this);

					for (MusicDirectory.Entry song : albumDirectory.getChildren())
					{
						if (!song.isVideo())
						{
							songs.add(song);
						}
					}
				}
			}

			@Override
			protected void done(List<MusicDirectory.Entry> songs)
			{
				if (Util.getShouldSortByDisc(SubsonicTabActivity.this))
				{
					Collections.sort(songs, new EntryByDiscAndTrackComparator());
				}

				DownloadService downloadService = getDownloadService();
				if (!songs.isEmpty() && downloadService != null)
				{
					if (!append && !playNext && !unpin && !background)
					{
						downloadService.clear();
					}
					warnIfNetworkOrStorageUnavailable();
					if (!background)
					{
						if (unpin)
						{
							downloadService.unpin(songs);
						}
						else
						{
							downloadService.download(songs, save, autoplay, playNext, shuffle, false);
							if (!append && Util.getShouldTransitionOnPlaybackPreference(SubsonicTabActivity.this))
							{
								Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
							}
						}
					}
					else
					{
						if (unpin)
						{
							downloadService.unpin(songs);
						}
						else
						{
							downloadService.downloadBackground(songs, save);
						}
					}
				}
			}
		};

		task.execute();
	}

	protected void playVideo(MusicDirectory.Entry entry)
	{
		if (!Util.isNetworkConnected(this))
		{
			Util.toast(this, R.string.select_album_no_network);
			return;
		}

		VideoPlayerType player = Util.getVideoPlayerType(this);

		try
		{
			player.playVideo(this, entry);
		}
		catch (Exception e)
		{
			Util.toast(this, e.getMessage(), false);
		}
	}

	protected void checkLicenseAndTrialPeriod(Runnable onValid)
	{
		if (licenseValid)
		{
			onValid.run();
			return;
		}

		int trialDaysLeft = Util.getRemainingTrialDays(this);
		Log.i(TAG, trialDaysLeft + " trial days left.");

		if (trialDaysLeft == 0)
		{
			showDonationDialog(trialDaysLeft, null);
		}
		else if (trialDaysLeft < Constants.FREE_TRIAL_DAYS / 2)
		{
			showDonationDialog(trialDaysLeft, onValid);
		}
		else
		{
			Util.toast(this, getResources().getString(R.string.select_album_not_licensed, trialDaysLeft));
			onValid.run();
		}
	}

	private void showDonationDialog(int trialDaysLeft, final Runnable onValid)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_info);

		if (trialDaysLeft == 0)
		{
			builder.setTitle(R.string.select_album_donate_dialog_0_trial_days_left);
		}
		else
		{
			builder.setTitle(getResources().getQuantityString(R.plurals.select_album_donate_dialog_n_trial_days_left, trialDaysLeft, trialDaysLeft));
		}

		builder.setMessage(R.string.select_album_donate_dialog_message);

		builder.setPositiveButton(R.string.select_album_donate_dialog_now, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int i)
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DONATION_URL)));
			}
		});

		builder.setNegativeButton(R.string.select_album_donate_dialog_later, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int i)
			{
				dialogInterface.dismiss();
				if (onValid != null)
				{
					onValid.run();
				}
			}
		});

		builder.create().show();
	}

	protected void setActionBarDisplayHomeAsUp(boolean enabled)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setDisplayHomeAsUpEnabled(enabled);
		}
	}

	protected void setActionBarTitle(CharSequence title)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setTitle(title);
		}
	}

	protected void setActionBarTitle(int id)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setTitle(id);
		}
	}

	protected CharSequence getActionBarTitle()
	{
		ActionBar actionBar = getActionBar();
		CharSequence title = null;

		if (actionBar != null)
		{
			title = actionBar.getTitle();
		}

		return title;
	}

	protected void setActionBarSubtitle(CharSequence title)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setSubtitle(title);
		}
	}

	protected void setActionBarSubtitle(int id)
	{
		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setSubtitle(id);
		}
	}

	protected CharSequence getActionBarSubtitle()
	{
		ActionBar actionBar = getActionBar();
		CharSequence subtitle = null;

		if (actionBar != null)
		{
			subtitle = actionBar.getSubtitle();
		}

		return subtitle;
	}

	private void setUncaughtExceptionHandler()
	{
		Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
		if (!(handler instanceof SubsonicUncaughtExceptionHandler))
		{
			Thread.setDefaultUncaughtExceptionHandler(new SubsonicUncaughtExceptionHandler(this));
		}
	}

	/**
	 * Logs the stack trace of uncaught exceptions to a file on the SD card.
	 */
	private static class SubsonicUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
	{

		private final Thread.UncaughtExceptionHandler defaultHandler;
		private final Context context;
		private static final String filename = "ultrasonic-stacktrace.txt";

		private SubsonicUncaughtExceptionHandler(Context context)
		{
			this.context = context;
			defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		}

		@Override
		public void uncaughtException(Thread thread, Throwable throwable)
		{
			File file = null;
			PrintWriter printWriter = null;

			try
			{
				file = new File(Environment.getExternalStorageDirectory(), filename);
				printWriter = new PrintWriter(file);
				printWriter.println("Android API level: " + Build.VERSION.SDK_INT);
				printWriter.println("UltraSonic version name: " + Util.getVersionName(context));
				printWriter.println("UltraSonic version code: " + Util.getVersionCode(context));
				printWriter.println();
				throwable.printStackTrace(printWriter);
				Log.i(TAG, "Stack trace written to " + file);
			}
			catch (Throwable x)
			{
				Log.e(TAG, "Failed to write stack trace to " + file, x);
			}
			finally
			{
				Util.close(printWriter);
				if (defaultHandler != null)
				{
					defaultHandler.uncaughtException(thread, throwable);
				}
			}
		}
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
				intent = new Intent(SubsonicTabActivity.this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
				break;
			case R.id.menu_browse:
				intent = new Intent(SubsonicTabActivity.this, SelectArtistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
				break;
			case R.id.menu_search:
				intent = new Intent(SubsonicTabActivity.this, SearchActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
				break;
			case R.id.menu_playlists:
				intent = new Intent(SubsonicTabActivity.this, SelectPlaylistActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
				break;
			case R.id.menu_chat:
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, ChatActivity.class);
				break;
			case R.id.menu_bookmarks:
				Util.startActivityWithoutTransition(this, BookmarkActivity.class);
				break;
			case R.id.menu_now_playing:
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
				break;
			case R.id.menu_settings:
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, SettingsActivity.class);
				break;
			case R.id.menu_about:
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, HelpActivity.class);
				break;
			case R.id.menu_exit:
				intent = new Intent(SubsonicTabActivity.this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
				Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
				break;
		}

		menuDrawer.closeMenu(true);
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

	static class SwipeDetector implements OnTouchListener
	{

		public static SwipeDetector Create(SubsonicTabActivity activity, final DownloadService downloadService)
		{
			SwipeDetector swipeDetector = new SwipeDetector();
			swipeDetector.downloadService = downloadService;
			swipeDetector.activity = activity;
			return swipeDetector;
		}

		private static final int MIN_DISTANCE = 30;
		private float downX, downY, upX, upY;
		private DownloadService downloadService;
		private SubsonicTabActivity activity;

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			switch (event.getAction())
			{
				case MotionEvent.ACTION_DOWN:
				{
					downX = event.getX();
					downY = event.getY();
					return false;
				}
				case MotionEvent.ACTION_UP:
				{
					upX = event.getX();
					upY = event.getY();

					float deltaX = downX - upX;
					float deltaY = downY - upY;

					if (Math.abs(deltaX) > MIN_DISTANCE)
					{
						// left or right
						if (deltaX < 0)
						{
							downloadService.previous();
							return false;
						}
						if (deltaX > 0)
						{
							downloadService.next();
							return false;
						}
					}
					else if (Math.abs(deltaY) > MIN_DISTANCE)
					{
						if (deltaY < 0)
						{
							SubsonicTabActivity.nowPlayingHidden = true;
							activity.hideNowPlaying();
							return false;
						}
						if (deltaY > 0)
						{
							return false;
						}
					}

					Util.startActivityWithoutTransition(activity, DownloadActivity.class);
					return false;
				}
			}

			return false;
		}
	}
}