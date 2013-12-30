package com.thejoshwa.ultrasonic.androidapp.provider;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.DownloadActivity;
import com.thejoshwa.ultrasonic.androidapp.activity.MainActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;

public class UltraSonicAppWidgetProvider extends AppWidgetProvider
{

	private final static String TAG = UltraSonicAppWidgetProvider.class.getSimpleName();
	protected int layoutId;

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		defaultAppWidget(context, appWidgetIds);
	}

	/**
	 * Initialize given widgets to default state, where we launch UltraSonic on default click
	 * and hide actions if service not running.
	 */
	private void defaultAppWidget(Context context, int[] appWidgetIds)
	{
		final Resources res = context.getResources();
		final RemoteViews views = new RemoteViews(context.getPackageName(), this.layoutId);

		views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text));

		linkButtons(context, views, false);
		pushUpdate(context, appWidgetIds, views);
	}

	private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views)
	{
		// Update specific list of appWidgetIds if given, otherwise default to all
		final AppWidgetManager manager = AppWidgetManager.getInstance(context);

		if (manager != null)
		{
			if (appWidgetIds != null)
			{
				manager.updateAppWidget(appWidgetIds, views);
			}
			else
			{
				manager.updateAppWidget(new ComponentName(context, this.getClass()), views);
			}
		}
	}

	/**
	 * Handle a change notification coming over from {@link DownloadService}
	 */
	public void notifyChange(Context context, DownloadService service, boolean playing, boolean setAlbum)
	{
		if (hasInstances(context))
		{
			performUpdate(context, service, null, playing, setAlbum);
		}
	}

	/**
	 * Check against {@link AppWidgetManager} if there are any instances of this widget.
	 */
	private boolean hasInstances(Context context)
	{
		AppWidgetManager manager = AppWidgetManager.getInstance(context);

		if (manager != null)
		{
			int[] appWidgetIds = manager.getAppWidgetIds(new ComponentName(context, getClass()));
			return (appWidgetIds.length > 0);
		}

		return false;
	}

	/**
	 * Update all active widget instances by pushing changes
	 */
	private void performUpdate(Context context, DownloadService service, int[] appWidgetIds, boolean playing, boolean setAlbum)
	{
		final Resources res = context.getResources();
		final RemoteViews views = new RemoteViews(context.getPackageName(), this.layoutId);

		MusicDirectory.Entry currentPlaying = service.getCurrentPlaying() == null ? null : service.getCurrentPlaying().getSong();
		String title = currentPlaying == null ? null : currentPlaying.getTitle();
		String artist = currentPlaying == null ? null : currentPlaying.getArtist();
		String album = currentPlaying == null ? null : currentPlaying.getAlbum();
		CharSequence errorState = null;

		// Show error message?
		String status = Environment.getExternalStorageState();
		if (status.equals(Environment.MEDIA_SHARED) || status.equals(Environment.MEDIA_UNMOUNTED))
		{
			errorState = res.getText(R.string.widget_sdcard_busy);
		}
		else if (status.equals(Environment.MEDIA_REMOVED))
		{
			errorState = res.getText(R.string.widget_sdcard_missing);
		}
		else if (currentPlaying == null)
		{
			errorState = res.getText(R.string.widget_initial_text);
		}

		if (errorState != null)
		{
			// Show error state to user
			views.setTextViewText(R.id.title, null);
			views.setTextViewText(R.id.artist, errorState);
			if (setAlbum)
			{
				views.setTextViewText(R.id.album, null);
			}
			views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_default);
		}
		else
		{
			// No error, so show normal titles
			views.setTextViewText(R.id.title, title);
			views.setTextViewText(R.id.artist, artist);
			if (setAlbum)
			{
				views.setTextViewText(R.id.album, album);
			}
		}

		// Set correct drawable for pause state
		if (playing)
		{
			views.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark);
		}
		else
		{
			views.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark);
		}

		// Set the cover art
		try
		{
			Bitmap bitmap = currentPlaying == null ? null : FileUtil.getAlbumArtBitmap(context, currentPlaying, 240, true);

			if (bitmap == null)
			{
				// Set default cover art
				views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_default);
			}
			else
			{
				views.setImageViewBitmap(R.id.appwidget_coverart, bitmap);
			}
		}
		catch (Exception x)
		{
			Log.e(TAG, "Failed to load cover art", x);
			views.setImageViewResource(R.id.appwidget_coverart, R.drawable.appwidget_art_default);
		}

		// Link actions buttons to intents
		linkButtons(context, views, currentPlaying != null);

		pushUpdate(context, appWidgetIds, views);
	}

	/**
	 * Link up various button actions using {@link PendingIntent}.
	 *
	 * @param playerActive True if player is active in background, which means
	 *                     widget click will launch {@link DownloadActivity},
	 *                     otherwise we launch {@link MainActivity}.
	 */
	private static void linkButtons(Context context, RemoteViews views, boolean playerActive)
	{

		Intent intent = new Intent(context, playerActive ? DownloadActivity.class : MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent);
		views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent);

		// Emulate media button clicks.
		intent = new Intent("1");
		intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
		intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_play, pendingIntent);

		intent = new Intent("2");  // Use a unique action name to ensure a different PendingIntent to be created.
		intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
		intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_next, pendingIntent);

		intent = new Intent("3");  // Use a unique action name to ensure a different PendingIntent to be created.
		intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
		intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.control_previous, pendingIntent);
	}
}
