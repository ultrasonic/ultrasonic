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
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

/**
 * An HTML-based help screen with Back and Done buttons at the bottom.
 *
 * @author Sindre Mehus
 */
public final class HelpActivity extends ResultActivity implements OnClickListener
{
	private WebView webView;
	private ImageView backButton;
	private ImageView forwardButton;

	private static final String STATE_MENUDRAWER = "com.thejoshwa.ultrasonic.androidapp.menuDrawer";
	private static final String STATE_ACTIVE_VIEW_ID = "com.thejoshwa.ultrasonic.androidapp.activeViewId";
	private static final String STATE_ACTIVE_POSITION = "com.thejoshwa.ultrasonic.androidapp.activePosition";

	public MenuDrawer menuDrawer;
	private int activePosition = 1;
	private int menuActiveViewId;
	View chatMenuItem;
	View bookmarksMenuItem;
	View sharesMenuItem;

	@Override
	protected void onCreate(Bundle bundle)
	{
		applyTheme();
		super.onCreate(bundle);
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.help);

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
		View aboutMenuItem = findViewById(R.id.menu_about);

		findViewById(R.id.menu_home).setOnClickListener(this);
		findViewById(R.id.menu_browse).setOnClickListener(this);
		findViewById(R.id.menu_search).setOnClickListener(this);
		findViewById(R.id.menu_playlists).setOnClickListener(this);
		sharesMenuItem.setOnClickListener(this);
		chatMenuItem.setOnClickListener(this);
		bookmarksMenuItem.setOnClickListener(this);
		findViewById(R.id.menu_now_playing).setOnClickListener(this);
		findViewById(R.id.menu_settings).setOnClickListener(this);
		aboutMenuItem.setOnClickListener(this);
		findViewById(R.id.menu_exit).setOnClickListener(this);

		ActionBar actionBar = getActionBar();

		if (actionBar != null)
		{
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		menuDrawer.setActiveView(aboutMenuItem);

		webView = (WebView) findViewById(R.id.help_contents);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebViewClient(new HelpClient());

		if (bundle != null)
		{
			webView.restoreState(bundle);
		}
		else
		{
			webView.loadUrl(getResources().getString(R.string.help_url));
		}

		backButton = (ImageView) findViewById(R.id.help_back);
		backButton.setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				webView.goBack();
			}
		});

		ImageView stopButton = (ImageView) findViewById(R.id.help_stop);
		stopButton.setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				webView.stopLoading();
				setProgressBarIndeterminateVisibility(false);
			}
		});

		forwardButton = (ImageView) findViewById(R.id.help_forward);
		forwardButton.setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				webView.goForward();
			}
		});
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

	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	protected void onSaveInstanceState(Bundle state)
	{
		webView.saveState(state);
		super.onSaveInstanceState(state);
		state.putParcelable(STATE_MENUDRAWER, menuDrawer.saveState());
		state.putInt(STATE_ACTIVE_VIEW_ID, menuActiveViewId);
		state.putInt(STATE_ACTIVE_POSITION, activePosition);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (webView.canGoBack())
			{
				webView.goBack();
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
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
	protected void onRestoreInstanceState(Bundle state)
	{
		super.onRestoreInstanceState(state);
		menuDrawer.restoreState(state.getParcelable(STATE_MENUDRAWER));
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

		finish();

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

	private final class HelpClient extends WebViewClient
	{
		@Override
		public void onLoadResource(WebView webView, String url)
		{
			setProgressBarIndeterminateVisibility(true);
			super.onLoadResource(webView, url);
		}

		@Override
		public void onPageFinished(WebView view, String url)
		{
			setProgressBarIndeterminateVisibility(false);
			String versionName = Util.getVersionName(HelpActivity.this);
			String title = String.format("%s (%s)", view.getTitle(), versionName);
			ActionBar actionBar = getActionBar();

			if (actionBar != null)
			{
				actionBar.setSubtitle(title);
			}

			backButton.setEnabled(view.canGoBack());
			forwardButton.setEnabled(view.canGoForward());
		}

		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
		{
			Util.toast(HelpActivity.this, description);
		}
	}
}
