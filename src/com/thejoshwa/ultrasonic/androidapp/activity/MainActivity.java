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

import java.util.Arrays;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.MergeAdapter;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends SubsonicTabActivity {

    private static final int MENU_GROUP_SERVER = 10;
    private static final int MENU_ITEM_SERVER_1 = 101;
    private static final int MENU_ITEM_SERVER_2 = 102;
    private static final int MENU_ITEM_SERVER_3 = 103;
    private static final int MENU_ITEM_OFFLINE = 104;

    private String theme;

    private static boolean infoDialogDisplayed;
    
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getIntent().hasExtra(Constants.INTENT_EXTRA_NAME_EXIT)) {
            exit();
            return;
        }
        
        setContentView(R.layout.main);

        loadSettings();
        
        View buttons = LayoutInflater.from(this).inflate(R.layout.main_buttons, null);

        final View serverButton = buttons.findViewById(R.id.main_select_server);
        final TextView serverTextView = (TextView) serverButton.findViewById(R.id.main_select_server_2);

        final View musicTitle = buttons.findViewById(R.id.main_music);
        final View artistsButton = buttons.findViewById(R.id.main_artists_button);
        final View albumsButton = buttons.findViewById(R.id.main_albums_button);
        final View genresButton = buttons.findViewById(R.id.main_genres_button);

        final View songsTitle = buttons.findViewById(R.id.main_songs);
        final View randomSongsButton = buttons.findViewById(R.id.main_songs_button);
        final View songsStarredButton = buttons.findViewById(R.id.main_songs_starred);

        final View albumsTitle = buttons.findViewById(R.id.main_albums);
        final View albumsNewestButton = buttons.findViewById(R.id.main_albums_newest);
        final View albumsRandomButton = buttons.findViewById(R.id.main_albums_random);
        final View albumsHighestButton = buttons.findViewById(R.id.main_albums_highest);
        final View albumsStarredButton = buttons.findViewById(R.id.main_albums_starred);        
        final View albumsRecentButton = buttons.findViewById(R.id.main_albums_recent);
        final View albumsFrequentButton = buttons.findViewById(R.id.main_albums_frequent);
        final View albumsAlphaByNameButton = buttons.findViewById(R.id.main_albums_alphaByName);
        final View albumsAlphaByArtistButton = buttons.findViewById(R.id.main_albums_alphaByArtist);
                
        final View dummyView = findViewById(R.id.main_dummy);

        int instance = Util.getActiveServer(this);
        String name = Util.getServerName(this, instance);
        serverTextView.setText(name);

        ListView list = (ListView) findViewById(R.id.main_list);
        
        MergeAdapter adapter = new MergeAdapter();
        adapter.addViews(Arrays.asList(serverButton), true);
        
        if (!Util.isOffline(this)) {
        	adapter.addView(musicTitle, false);
      		adapter.addViews(Arrays.asList(artistsButton, albumsButton, genresButton), true);
        	adapter.addView(songsTitle, false);
        	adapter.addViews(Arrays.asList(randomSongsButton, songsStarredButton), true);
            adapter.addView(albumsTitle, false);
            adapter.addViews(Arrays.asList(albumsNewestButton, albumsRecentButton, albumsFrequentButton, albumsHighestButton, albumsRandomButton, albumsStarredButton, albumsAlphaByNameButton, albumsAlphaByArtistButton), true);
        }
        
        list.setAdapter(adapter);
        registerForContextMenu(dummyView);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (view == serverButton) {
                    dummyView.showContextMenu();
                } else if (view == albumsNewestButton) {
                    showAlbumList("newest", R.string.main_albums_newest);
                } else if (view == albumsRandomButton) {
                    showAlbumList("random", R.string.main_albums_random);
                } else if (view == albumsHighestButton) {
                    showAlbumList("highest", R.string.main_albums_highest);
                } else if (view == albumsRecentButton) {
                    showAlbumList("recent", R.string.main_albums_recent);
                } else if (view == albumsFrequentButton) {
                    showAlbumList("frequent", R.string.main_albums_frequent);
                } else if (view == albumsStarredButton) {
                    showAlbumList("starred", R.string.main_albums_starred);
                } else if (view == albumsAlphaByNameButton) {
                	showAlbumList("alphabeticalByName", R.string.main_albums_alphaByName);
                } else if (view == albumsAlphaByArtistButton) {
                	showAlbumList("alphabeticalByArtist", R.string.main_albums_alphaByArtist);
                } else if (view == songsStarredButton) {
                	showStarredSongs();
                } else if (view == artistsButton) {
                	showArtists();
                } else if (view == albumsButton) {
                	showAlbumList("alphabeticalByName", R.string.main_albums_title);
                } else if (view == randomSongsButton) {
                	showRandomSongs();
                } else if (view == genresButton) {
                	showGenres();
                }
            }
        });
        
        View homeMenuItem = findViewById(R.id.menu_home);
        menuDrawer.setActiveView(homeMenuItem);

        getActionBar().setTitle(R.string.common_appname);
        setTitle(R.string.common_appname);

        // Remember the current theme.
        theme = Util.getTheme(this);

        showInfoDialog();
    }

    private void loadSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
        SharedPreferences prefs = Util.getPreferences(this);
        if (!prefs.contains(Constants.PREFERENCES_KEY_CACHE_LOCATION)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, FileUtil.getDefaultMusicDirectory().getPath());
            editor.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Restart activity if theme has changed.
        if (theme != null && !theme.equals(Util.getTheme(this))) {
            restart();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main, menu);
    	super.onCreateOptionsMenu(menu);
    	
    	return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        MenuItem menuItem1 = menu.add(MENU_GROUP_SERVER, MENU_ITEM_SERVER_1, MENU_ITEM_SERVER_1, Util.getServerName(this, 1));
        MenuItem menuItem2 = menu.add(MENU_GROUP_SERVER, MENU_ITEM_SERVER_2, MENU_ITEM_SERVER_2, Util.getServerName(this, 2));
        MenuItem menuItem3 = menu.add(MENU_GROUP_SERVER, MENU_ITEM_SERVER_3, MENU_ITEM_SERVER_3, Util.getServerName(this, 3));
        MenuItem menuItem4 = menu.add(MENU_GROUP_SERVER, MENU_ITEM_OFFLINE, MENU_ITEM_OFFLINE, Util.getServerName(this, 0));
        menu.setGroupCheckable(MENU_GROUP_SERVER, true, true);
        menu.setHeaderTitle(R.string.main_select_server);

        switch (Util.getActiveServer(this)) {
            case 0:
                menuItem4.setChecked(true);
                break;
            case 1:
                menuItem1.setChecked(true);
                break;
            case 2:
                menuItem2.setChecked(true);
                break;
            case 3:
                menuItem3.setChecked(true);
                break;
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case MENU_ITEM_OFFLINE:
                setActiveServer(0);
                break;
            case MENU_ITEM_SERVER_1:
                setActiveServer(1);
                break;
            case MENU_ITEM_SERVER_2:
                setActiveServer(2);
                break;
            case MENU_ITEM_SERVER_3:
                setActiveServer(3);
                break;
            default:
                return super.onContextItemSelected(menuItem);
        }

        // Restart activity
        restart();
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			menuDrawer.toggleMenu();
    			return true;                
            case R.id.main_shuffle:
            	Intent intent1 = new Intent(this, DownloadActivity.class);
            	intent1.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
            	Util.startActivityWithoutTransition(this, intent1);
                return true;
        }

        return false;
    }
    
    private void setActiveServer(int instance) {
        if (Util.getActiveServer(this) != instance) {
            DownloadService service = getDownloadService();
            if (service != null) {
                service.clearIncomplete();
            }
            Util.setActiveServer(this, instance);
        }
    }

    private void restart() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Util.startActivityWithoutTransition(this, intent);
    }

    private void exit() {
        stopService(new Intent(this, DownloadServiceImpl.class));
        Util.unregisterMediaButtonEventReceiver(this);
        finish();
    }

    private void showInfoDialog() {
        if (!infoDialogDisplayed) {
            infoDialogDisplayed = true;
            if (Util.getRestUrl(this, null).contains("yourhost")) {
                Util.info(this, R.string.main_welcome_title, R.string.main_welcome_text);
            }
        }
    }

    private void showAlbumList(String type, int title) {
        Intent intent = new Intent(this, SelectAlbumActivity.class);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, title);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxAlbums(this));
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
		Util.startActivityWithoutTransition(this, intent);
	}
    
    private void showStarredSongs() {
    	Intent intent = new Intent(this, SelectAlbumActivity.class);
    	intent.putExtra(Constants.INTENT_EXTRA_NAME_STARRED, 1);
    	Util.startActivityWithoutTransition(this, intent);
    }
    
    private void showRandomSongs() {
    	Intent intent = new Intent(this, SelectAlbumActivity.class);
    	intent.putExtra(Constants.INTENT_EXTRA_NAME_RANDOM, 1);
    	intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxSongs(this));
    	Util.startActivityWithoutTransition(this, intent);
    }
    
    private void showArtists() {
    	Intent intent = new Intent(this, SelectArtistActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, getResources().getString(R.string.main_artists_title));
    	Util.startActivityWithoutTransition(this, intent);
    }
    
    private void showGenres() {
    	Intent intent = new Intent(this, SelectGenreActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	Util.startActivityWithoutTransition(this, intent);
    }
}