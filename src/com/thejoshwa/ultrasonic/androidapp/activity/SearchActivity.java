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

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchCriteria;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.util.BackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.MergeAdapter;
import com.thejoshwa.ultrasonic.androidapp.util.TabActivityBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.view.ArtistAdapter;
import com.thejoshwa.ultrasonic.androidapp.view.EntryAdapter;

/**
 * Performs searches and displays the matching artists, albums and songs.
 *
 * @author Sindre Mehus
 */
public class SearchActivity extends SubsonicTabActivity {

    private static int DEFAULT_ARTISTS;
    private static int DEFAULT_ALBUMS;
    private static int DEFAULT_SONGS;

    private ListView list;

    private View artistsHeading;
    private View albumsHeading;
    private View songsHeading;
    private TextView searchButton;
    private View moreArtistsButton;
    private View moreAlbumsButton;
    private View moreSongsButton;
    private SearchResult searchResult;
    private MergeAdapter mergeAdapter;
    private ArtistAdapter artistAdapter;
    private ListAdapter moreArtistsAdapter;
    private EntryAdapter albumAdapter;
    private ListAdapter moreAlbumsAdapter;
    private ListAdapter moreSongsAdapter;
    private EntryAdapter songAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        setActionBarTitle(R.string.common_appname);
        setActionBarSubtitle(R.string.search_title);

        View searchMenuItem = findViewById(R.id.menu_search);
        menuDrawer.setActiveView(searchMenuItem);

        DEFAULT_ARTISTS = Util.getDefaultArtists(this);
        DEFAULT_ALBUMS = Util.getDefaultAlbums(this);
        DEFAULT_SONGS = Util.getDefaultSongs(this);
        
        View buttons = LayoutInflater.from(this).inflate(R.layout.search_buttons, null);

        artistsHeading = buttons.findViewById(R.id.search_artists);
        albumsHeading = buttons.findViewById(R.id.search_albums);
        songsHeading = buttons.findViewById(R.id.search_songs);

        searchButton = (TextView) buttons.findViewById(R.id.search_search);
        moreArtistsButton = buttons.findViewById(R.id.search_more_artists);
        moreAlbumsButton = buttons.findViewById(R.id.search_more_albums);
        moreSongsButton = buttons.findViewById(R.id.search_more_songs);

        list = (ListView) findViewById(R.id.search_list);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (view == searchButton) {
                    onSearchRequested();
                } else if (view == moreArtistsButton) {
                    expandArtists();
                } else if (view == moreAlbumsButton) {
                    expandAlbums();
                } else if (view == moreSongsButton) {
                    expandSongs();
                } else {
                    Object item = parent.getItemAtPosition(position);
                    if (item instanceof Artist) {
                        onArtistSelected((Artist) item);
                    } else if (item instanceof MusicDirectory.Entry) {
                        MusicDirectory.Entry entry = (MusicDirectory.Entry) item;
                        if (entry.isDirectory()) {
                            onAlbumSelected(entry, false);
                        } else if (entry.isVideo()) {
                            onVideoSelected(entry);
                        } else {
                            onSongSelected(entry, false, true, true, false);
                        }

                    }
                }
            }
        });
        
        registerForContextMenu(list);

        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String query = intent.getStringExtra(Constants.INTENT_EXTRA_NAME_QUERY);
        boolean autoplay = intent.getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);
        boolean requestsearch = intent.getBooleanExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, false);

        if (query != null) {
            mergeAdapter = new MergeAdapter();
            list.setAdapter(mergeAdapter);
            search(query, autoplay);
        } else {
            populateList();
            if (requestsearch)
                onSearchRequested();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Object selectedItem = list.getItemAtPosition(info.position);

        boolean isArtist = selectedItem instanceof Artist;
        boolean isAlbum = selectedItem instanceof MusicDirectory.Entry && ((MusicDirectory.Entry) selectedItem).isDirectory();
        
        if (!isArtist && !isAlbum) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.select_song_context, menu);
        } else {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.select_album_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        if (info == null) {
            return true;
        }

        Object selectedItem = list.getItemAtPosition(info.position);

        Artist artist = selectedItem instanceof Artist ? (Artist) selectedItem : null;
        Entry entry = selectedItem instanceof Entry ? (Entry) selectedItem : null;

        String entryId = null;

        if (entry != null) {
            entryId = entry.getId();
        }

        String id = artist != null ? artist.getId() : entryId;

        if (id == null ){
            return true;
        }

        List<Entry> songs = new ArrayList<Entry>(1);

        switch (menuItem.getItemId()) {
            case R.id.album_menu_play_now:
                downloadRecursively(id, false, false, true, false, false, false, false);
                break;
            case R.id.album_menu_play_next:
                downloadRecursively(id, false, true, false, true, false, true, false);
                break;
            case R.id.album_menu_play_last:
                downloadRecursively(id, false, true, false, false, false, false, false);
                break;
            case R.id.album_menu_pin:
                downloadRecursively(id, true, true, false, false, false, false, false);
                break;
            case R.id.album_menu_unpin:
                downloadRecursively(id, false, false, false, false, false, false, true);
                break;
            case R.id.album_menu_download:
                downloadRecursively(id, false, false, false, false, true, false, false);
                break;
            case R.id.song_menu_play_now:
            	if (entry != null) {
            		songs = new ArrayList<MusicDirectory.Entry>(1);
            		songs.add(entry);
            		download(false, false, true, false, false, songs);
            	}
                break;
            case R.id.song_menu_play_next:
            	if (entry != null) {
            		songs = new ArrayList<MusicDirectory.Entry>(1);
            		songs.add(entry);
            		download(true, false, false, true, false, songs);
            	}
                break;
            case R.id.song_menu_play_last:
            	if (entry != null) {
            		songs = new ArrayList<MusicDirectory.Entry>(1);
            		songs.add(entry);
            		download(true, false, false, false, false, songs);
            	}
                break;
            case R.id.song_menu_pin:
            	if (entry != null) {
            		songs.add(entry);
            		Util.toast(SearchActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
            		downloadBackground(true, songs);
            	}
                break;
            case R.id.song_menu_download:
            	if (entry != null) {
            		songs.add(entry);
            		Util.toast(SearchActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_downloaded, songs.size(), songs.size()));
            		downloadBackground(false, songs);
            	}
                break;
            case R.id.song_menu_unpin:
            	if (entry != null) {
            		songs.add(entry);
            		Util.toast(SearchActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
            		getDownloadService().unpin(songs);
            	}
                break;
            default:
                return super.onContextItemSelected(menuItem);
        }

        return true;
    }
    
	private void downloadBackground(final boolean save, final List<MusicDirectory.Entry> songs) {
		if (getDownloadService() == null) {
			return;
		}

		Runnable onValid = new Runnable() {
			@Override
			public void run() {
				warnIfNetworkOrStorageUnavailable();
				getDownloadService().downloadBackground(songs, save);
			}
		};
		
		checkLicenseAndTrialPeriod(onValid);
	}
	
    private void search(final String query, final boolean autoplay) {
    	final int maxArtists = Util.getMaxArtists(this);
    	final int maxAlbums = Util.getMaxAlbums(this);
    	final int maxSongs = Util.getMaxSongs(this);
    	
        BackgroundTask<SearchResult> task = new TabActivityBackgroundTask<SearchResult>(this, true) {
            @Override
            protected SearchResult doInBackground() throws Throwable {
                SearchCriteria criteria = new SearchCriteria(query, maxArtists, maxAlbums, maxSongs);
                MusicService service = MusicServiceFactory.getMusicService(SearchActivity.this);
                licenseValid = service.isLicenseValid(SearchActivity.this, this);
                return service.search(criteria, SearchActivity.this, this);
            }

            @Override
            protected void done(SearchResult result) {
                searchResult = result;
                
                populateList();
                
                if (autoplay) {
                    autoplay();
                }

            }
        };
        task.execute();
    }

    private void populateList() {
        mergeAdapter = new MergeAdapter();
        mergeAdapter.addView(searchButton, true);

        if (searchResult != null) {
            List<Artist> artists = searchResult.getArtists();
            if (!artists.isEmpty()) {
                mergeAdapter.addView(artistsHeading);
                List<Artist> displayedArtists = new ArrayList<Artist>(artists.subList(0, Math.min(DEFAULT_ARTISTS, artists.size())));
                artistAdapter = new ArtistAdapter(this, displayedArtists);
                mergeAdapter.addAdapter(artistAdapter);
                if (artists.size() > DEFAULT_ARTISTS) {
                    moreArtistsAdapter = mergeAdapter.addView(moreArtistsButton, true);
                }
            }

            List<MusicDirectory.Entry> albums = searchResult.getAlbums();
            if (!albums.isEmpty()) {
                mergeAdapter.addView(albumsHeading);
                List<MusicDirectory.Entry> displayedAlbums = new ArrayList<MusicDirectory.Entry>(albums.subList(0, Math.min(DEFAULT_ALBUMS, albums.size())));
                albumAdapter = new EntryAdapter(this, getImageLoader(), displayedAlbums, false);
                mergeAdapter.addAdapter(albumAdapter);
                if (albums.size() > DEFAULT_ALBUMS) {
                    moreAlbumsAdapter = mergeAdapter.addView(moreAlbumsButton, true);
                }
            }

            List<MusicDirectory.Entry> songs = searchResult.getSongs();
            if (!songs.isEmpty()) {
                mergeAdapter.addView(songsHeading);
                List<MusicDirectory.Entry> displayedSongs = new ArrayList<MusicDirectory.Entry>(songs.subList(0, Math.min(DEFAULT_SONGS, songs.size())));
                songAdapter = new EntryAdapter(this, getImageLoader(), displayedSongs, false);
                mergeAdapter.addAdapter(songAdapter);
                if (songs.size() > DEFAULT_SONGS) {
                    moreSongsAdapter = mergeAdapter.addView(moreSongsButton, true);
                }
            }

            boolean empty = searchResult.getArtists().isEmpty() && searchResult.getAlbums().isEmpty() && searchResult.getSongs().isEmpty();
            searchButton.setText(empty ? R.string.search_no_match : R.string.search_search);
        }

        list.setAdapter(mergeAdapter);
    }

    private void expandArtists() {
        artistAdapter.clear();
        
        for (Artist artist : searchResult.getArtists()) {
            artistAdapter.add(artist);
        }
        
        artistAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreArtistsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void expandAlbums() {
        albumAdapter.clear();
        
        for (MusicDirectory.Entry album : searchResult.getAlbums()) {
            albumAdapter.add(album);
        }
        
        albumAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreAlbumsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void expandSongs() {
        songAdapter.clear();
        
        for (MusicDirectory.Entry song : searchResult.getSongs()) {
            songAdapter.add(song);
        }
        
        songAdapter.notifyDataSetChanged();
        mergeAdapter.removeAdapter(moreSongsAdapter);
        mergeAdapter.notifyDataSetChanged();
    }

    private void onArtistSelected(Artist artist) {
        Intent intent = new Intent(this, SelectAlbumActivity.class);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, artist.getId());
        intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, artist.getName());
        Util.startActivityWithoutTransition(this, intent);
    }

    private void onAlbumSelected(MusicDirectory.Entry album, boolean autoplay) {
        Intent intent = new Intent(SearchActivity.this, SelectAlbumActivity.class);
        intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, album.getId());
        intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, album.getTitle());
        intent.putExtra(Constants.INTENT_EXTRA_NAME_IS_ALBUM, album.isDirectory());
        intent.putExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, autoplay);
        Util.startActivityWithoutTransition(SearchActivity.this, intent);
    }

    private void onSongSelected(MusicDirectory.Entry song, boolean save, boolean append, boolean autoplay, boolean playNext) {
        DownloadService downloadService = getDownloadService();
        if (downloadService != null) {
            if (!append && !playNext) {
                downloadService.clear();
            }
            
            downloadService.download(Arrays.asList(song), save, false, playNext, false, false);
            
            if (autoplay) {
                downloadService.play(downloadService.size() - 1);
            }

            Util.toast(SearchActivity.this, getResources().getQuantityString(R.plurals.select_album_n_songs_added, 1, 1));
        }
    }

    private void onVideoSelected(MusicDirectory.Entry entry) {
    	playVideo(entry);
    }

    private void autoplay() {
        if (!searchResult.getSongs().isEmpty()) {
            onSongSelected(searchResult.getSongs().get(0), false, false, true, false);
        } else if (!searchResult.getAlbums().isEmpty()) {
            onAlbumSelected(searchResult.getAlbums().get(0), true);
        }
    }
}