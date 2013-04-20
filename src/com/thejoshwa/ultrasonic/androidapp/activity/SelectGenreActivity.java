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
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Genre;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.GenreAdapter;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.util.ArrayList;
import java.util.List;

public class SelectGenreActivity extends SubsonicTabActivity implements AdapterView.OnItemClickListener {

	private static final String TAG = SelectGenreActivity.class.getSimpleName();
	
	private ListView genreList;
    private View emptyView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_genre);

        genreList = (ListView) findViewById(R.id.select_genre_list);
        genreList.setOnItemClickListener(this);
        genreList.setOnTouchListener(gestureListener);
        
        emptyView = findViewById(R.id.select_genre_empty);

        registerForContextMenu(genreList);

        setTitle(R.string.main_genres_title);

        load();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main, menu);
    	inflater.inflate(R.menu.select_common, menu);
    	super.onCreateOptionsMenu(menu);
    	
    	return true;
    }

    private void refresh() {
        finish();
        Intent intent = getIntent();
        intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
        Util.startActivityWithoutTransition(this, intent);
    }

    private void load() {
        BackgroundTask<List<Genre>> task = new TabActivityBackgroundTask<List<Genre>>(this) {
            @Override
            protected List<Genre> doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(SelectGenreActivity.this);
                
                List<Genre> genres = new ArrayList<Genre>(); 
                
                try {
                	genres = musicService.getGenres(SelectGenreActivity.this, this);
                } catch (Exception x) {
                    Log.e(TAG, "Failed to load genres", x);
                }
                
				return genres;
            }

            @Override
            protected void done(List<Genre> result) {
        		emptyView.setVisibility(result == null || result.isEmpty() ? View.VISIBLE : View.GONE);
            	
            	if (result != null) {
            		genreList.setAdapter(new GenreAdapter(SelectGenreActivity.this, result));
            	}
            		
            }
        };
        task.execute();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    	Genre genre = (Genre) parent.getItemAtPosition(position);
    	Intent intent = new Intent(this, SelectAlbumActivity.class);
    	intent.putExtra(Constants.INTENT_EXTRA_NAME_GENRE_NAME, genre.getName());
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxSongs(this));
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
    	Util.startActivityWithoutTransition(this, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_refresh:
            	refresh();
                return true;
            
            case R.id.main_shuffle:
                Intent intent = new Intent(this, DownloadActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
                Util.startActivityWithoutTransition(this, intent);
                return true;
                
            case R.id.menu_exit:
                Intent intent1 = new Intent(this, MainActivity.class);
                intent1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent1.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
                Util.startActivityWithoutTransition(this, intent1);
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.menu_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;                
        }

        return false;
    }
}