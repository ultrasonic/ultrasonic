/*
 This file is part of Ultrasonic.

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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import timber.log.Timber;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.*;
import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;
import static org.koin.java.KoinJavaComponent.inject;

import org.koin.java.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.fragment.SelectAlbumFragment;
import org.moire.ultrasonic.service.*;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.subsonic.SubsonicImageLoaderProxy;
import org.moire.ultrasonic.subsonic.loader.image.SubsonicImageLoader;
import org.moire.ultrasonic.util.*;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;

import kotlin.Lazy;

/**
 * @author Sindre Mehus
 */
public class SubsonicTabActivity extends ResultActivity
{
	private static final Pattern COMPILE = Pattern.compile(":");
	protected static String theme;
	private static SubsonicTabActivity instance;

	private boolean destroyed;

	private static final String STATE_MENUDRAWER = "org.moire.ultrasonic.menuDrawer";
	private static final String STATE_ACTIVE_VIEW_ID = "org.moire.ultrasonic.activeViewId";
	private static final String STATE_ACTIVE_POSITION = "org.moire.ultrasonic.activePosition";
	private static final int DIALOG_ASK_FOR_SHARE_DETAILS = 102;

	private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
	private final Lazy<MediaPlayerLifecycleSupport> lifecycleSupport = inject(MediaPlayerLifecycleSupport.class);
	protected Lazy<ImageLoaderProvider> imageLoader = inject(ImageLoaderProvider.class);

	public MenuDrawer menuDrawer;
	private int activePosition = 1;
	private int menuActiveViewId;
	private View nowPlayingView;
	View chatMenuItem;
	View bookmarksMenuItem;
	View sharesMenuItem;
	public static boolean nowPlayingHidden;
	boolean licenseValid;
	private EditText shareDescription;
	TimeSpanPicker timeSpanPicker;
	CheckBox hideDialogCheckBox;
	CheckBox noExpirationCheckBox;
	CheckBox saveAsDefaultsCheckBox;
	ShareDetails shareDetails;

	@Override
	protected void onCreate(Bundle bundle)
	{
		super.onCreate(bundle);

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
		sharesMenuItem = findViewById(R.id.menu_shares);

		//setActionBarDisplayHomeAsUp(true);

		TextView activeView = (TextView) findViewById(menuActiveViewId);

		if (activeView != null)
		{
			menuDrawer.setActiveView(activeView);
		}
	}

	@Override
	protected void onPostCreate(Bundle bundle)
	{
		super.onPostCreate(bundle);
		instance = this;

		int visibility = ActiveServerProvider.Companion.isOffline(this) ? View.GONE : View.VISIBLE;
		chatMenuItem.setVisibility(visibility);
		bookmarksMenuItem.setVisibility(visibility);
		sharesMenuItem.setVisibility(visibility);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Util.applyTheme(this);
		instance = this;

		Util.registerMediaButtonEventReceiver(this, false);
		// Lifecycle support's constructor registers some event receivers so it should be created early
		lifecycleSupport.getValue().onCreate();

		// Make sure to update theme
		if (theme != null && !theme.equals(Util.getTheme(this)))
		{
			theme = Util.getTheme(this);
			restart();
		}

		// This must be filled here because onCreate is called before the derived objects would call setContentView
		getNowPlayingView();

		if (!nowPlayingHidden)
		{
			//showNowPlaying();
		}
		else
		{
			//hideNowPlaying();
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
		Util.unregisterMediaButtonEventReceiver(this, false);
		super.onDestroy();
		destroyed = true;
		nowPlayingView = null;
		imageLoader.getValue().clearImageLoader();
	}

	protected void restart()
	{
		Intent intent = new Intent(this, this.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtras(getIntent());
		startActivityForResultWithoutTransition(this, intent);
		Timber.d("Restarting activity...");
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

	private void getNowPlayingView()
	{
		if (nowPlayingView == null)
		{
			try {
				nowPlayingView = findViewById(R.id.now_playing);
			}
			catch (Exception exception) {
				Timber.w(exception, "An exception has occurred while trying to get the nowPlayingView by findViewById");
			}
		}
	}

	public static SubsonicTabActivity getInstance()
	{
		return instance;
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


}
