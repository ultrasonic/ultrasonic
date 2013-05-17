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

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.service.OfflineException;
import com.thejoshwa.ultrasonic.androidapp.service.ServerTooOldException;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.ImageLoader;
import com.thejoshwa.ultrasonic.androidapp.util.LoadingTask;
import com.thejoshwa.ultrasonic.androidapp.util.ModalBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.SilentBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

/**
 * @author Sindre Mehus
 */
public class SubsonicTabActivity extends Activity implements OnClickListener{
    private static final String TAG = SubsonicTabActivity.class.getSimpleName();
    private static ImageLoader IMAGE_LOADER;
	protected static String theme;
    private static SubsonicTabActivity instance;
    
    private boolean destroyed;
    
    private static final String STATE_MENUDRAWER = "com.thejoshwa.ultrasonic.androidapp.menuDrawer";
    private static final String STATE_ACTIVE_VIEW_ID = "com.thejoshwa.ultrasonic.androidapp.activeViewId";
    private static final String STATE_ACTIVE_POSITION = "com.thejoshwa.ultrasonic.androidapp.activePosition";
    
    public MenuDrawer menuDrawer;    
    private int activePosition = 1;
    private int menuActiveViewId;
    private View nowPlayingView = null;
    View searchMenuItem = null;
    View playlistsMenuItem = null;
    View menuMain = null;
    public static boolean nowPlayingHidden = false;
    
    @Override
    protected void onCreate(Bundle bundle) {
        setUncaughtExceptionHandler();
        applyTheme();
        super.onCreate(bundle);

        startService(new Intent(this, DownloadServiceImpl.class));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
		if (bundle != null) {
            activePosition = bundle.getInt(STATE_ACTIVE_POSITION);
            menuActiveViewId = bundle.getInt(STATE_ACTIVE_VIEW_ID);
        }

       	menuDrawer = MenuDrawer.attach(this, MenuDrawer.MENU_DRAG_WINDOW, Position.LEFT);
        menuDrawer.setMenuView(R.layout.menu_main);

        searchMenuItem = findViewById(R.id.menu_search);
        playlistsMenuItem = findViewById(R.id.menu_playlists);
        
        findViewById(R.id.menu_home).setOnClickListener(this);
        findViewById(R.id.menu_browse).setOnClickListener(this);
        searchMenuItem.setOnClickListener(this);
        playlistsMenuItem.setOnClickListener(this);
        findViewById(R.id.menu_now_playing).setOnClickListener(this);
        findViewById(R.id.menu_settings).setOnClickListener(this);
        findViewById(R.id.menu_about).setOnClickListener(this);
        findViewById(R.id.menu_exit).setOnClickListener(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        
        TextView activeView = (TextView)findViewById(menuActiveViewId);
        
        if (activeView != null) {
            menuDrawer.setActiveView(activeView);
        }
    }
    
	@Override
	protected void onPostCreate(Bundle bundle) {
		super.onPostCreate(bundle);
        instance = this;
	}

    @Override
    protected void onResume() {
        super.onResume();
        applyTheme();
        instance = this;
       
        Util.registerMediaButtonEventReceiver(this);
        
		// Make sure to update theme
        if (theme != null && !theme.equals(Util.getTheme(this))) {
        	theme = Util.getTheme(this);
            restart();
        }
        
        if (!nowPlayingHidden) {
			showNowPlaying();
		} else {
			hideNowPlaying();
		}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case android.R.id.home:
        		menuDrawer.toggleMenu();
        		return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
    	Util.unregisterMediaButtonEventReceiver(this);
        super.onDestroy();
        destroyed = true;
        getImageLoader().clear();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
        boolean isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
        boolean isVolumeAdjust = isVolumeDown || isVolumeUp;
        boolean isJukebox = getDownloadService() != null && getDownloadService().isJukeboxEnabled();

        if (isVolumeAdjust && isJukebox) {
            getDownloadService().adjustJukeboxVolume(isVolumeUp);
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
	protected void restart() {
        Intent intent = new Intent(this, this.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtras(getIntent());
        Util.startActivityWithoutTransition(this, intent);
    }

    @Override
    public void finish() {
        super.finish();
        Util.disablePendingTransition(this);
    }
    
    public boolean isDestroyed() {
        return destroyed;
    }
    
    public void showNowPlaying()
    {
    	nowPlayingView = findViewById(R.id.now_playing);
    	
    	if (!Util.getShowNowPlayingPreference(this)) {
    		if (nowPlayingView != null) {
    			nowPlayingView.setVisibility(View.GONE);
    		}
    		return;
    	}
		
		if (nowPlayingView != null) {
			final DownloadService downloadService = DownloadServiceImpl.getInstance();
			
			if (downloadService != null) {
				PlayerState playerState = downloadService.getPlayerState();
				
				if (playerState.equals(PlayerState.PAUSED) || playerState.equals(PlayerState.STARTED)) {
					DownloadFile file = downloadService.getCurrentPlaying();
					
					if (file != null) {
						final Entry song = file.getSong();						
						showNowPlaying(this, (DownloadServiceImpl)downloadService, song, playerState);
					}
				} else {
					hideNowPlaying();
				}
				
				ImageView nowPlayingControlPlay = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);	
				
				SwipeDetector swipeDetector = SwipeDetector.Create(SubsonicTabActivity.this, downloadService);
				nowPlayingView.setOnTouchListener(swipeDetector);
				
				nowPlayingView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
					}
				});
			
				nowPlayingControlPlay.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						downloadService.togglePlayPause();
					}
				});				
			}
		}
    }

    private void applyTheme() {
        String theme = Util.getTheme(this);
        
        if ("dark".equalsIgnoreCase(theme) || "fullscreen".equalsIgnoreCase(theme)) {
            setTheme(R.style.UltraSonicTheme);
        } else if ("light".equalsIgnoreCase(theme) || "fullscreenlight".equalsIgnoreCase(theme)) {
            setTheme(R.style.UltraSonicTheme_Light);
        }
    }
    
    private void showNowPlaying(final Context context, final DownloadServiceImpl downloadService, final MusicDirectory.Entry song, final PlayerState playerState) {
    	this.runOnUiThread( new Runnable() {
    	    @Override 
    	    public void run() {
    	    	nowPlayingView = findViewById(R.id.now_playing);
    	    	
    	    	if (!Util.getShowNowPlayingPreference(SubsonicTabActivity.this)) {
    	    		if (nowPlayingView != null) {
    	    			nowPlayingView.setVisibility(View.GONE);
    	    		}
    	    		return;
    	    	}
    	    	
    	    	if (nowPlayingView != null) {
    				nowPlayingView.setVisibility(View.VISIBLE);
    				nowPlayingHidden = false;
    				
    				String title = song.getTitle();
    				String artist = song.getArtist();
    				
    				try {
    					ImageView nowPlayingImage = (ImageView) nowPlayingView.findViewById(R.id.now_playing_image);
    					TextView nowPlayingTrack = (TextView) nowPlayingView.findViewById(R.id.now_playing_trackname);
    					TextView nowPlayingArtist = (TextView) nowPlayingView.findViewById(R.id.now_playing_artist);

    			        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    			        int imageSizeLarge = (int) Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));
    					
    			        int size = 64;
    			        
    			        if (imageSizeLarge <= 480) {
    			        	size = 64;
    			        } else if (imageSizeLarge <= 768) {
    			        	size = 128;
    			        } else if (imageSizeLarge <= 1024) {
    			        	size = 256;
    			        } else if (imageSizeLarge <= 1080) {
    			        	size = imageSizeLarge;
    			        }

    					Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, song, size);

    					if (bitmap == null) {
    						// set default album art
    						nowPlayingImage.setImageResource(R.drawable.unknown_album);
    					} else {
    						nowPlayingImage.setImageBitmap(bitmap);
    					}
    					
    					nowPlayingImage.setOnClickListener(new View.OnClickListener() {
    			            @Override
    			            public void onClick(View view) {
    			                Intent intent = new Intent(SubsonicTabActivity.this, SelectAlbumActivity.class);
    			                intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getParent());
    			                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, song.getAlbum());
    			                Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
    			            }
    			        });
    					
    					nowPlayingTrack.setText(title);
    					nowPlayingArtist.setText(artist);

    				} catch (Exception x) {
    					Log.w(TAG, "Failed to get notification cover art", x);
    				}

    				ImageView playButton = (ImageView) nowPlayingView.findViewById(R.id.now_playing_control_play);

    				if (playerState == PlayerState.PAUSED) {
    					playButton.setImageDrawable(Util.getDrawableFromAttribute(SubsonicTabActivity.this, R.attr.media_play));
    				} else if (playerState == PlayerState.STARTED) {
    					playButton.setImageDrawable(Util.getDrawableFromAttribute(SubsonicTabActivity.this, R.attr.media_pause));
    				}
    			}
    	    }
    	});
    }

    public void hideNowPlaying() {
    	this.runOnUiThread( new Runnable() {
    		@Override 
    	    public void run() {
    			nowPlayingView = findViewById(R.id.now_playing);
    	
    			if (nowPlayingView != null) {
    				nowPlayingView.setVisibility(View.GONE);
    			}
    		}
    	});
    }   

    public static SubsonicTabActivity getInstance() {
        return instance;
    }
    
    public boolean getIsDestroyed() {
        return destroyed;
    }

    public void setProgressVisible(boolean visible) {
        View view = findViewById(R.id.tab_progress);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void updateProgress(String message) {
        TextView view = (TextView) findViewById(R.id.tab_progress_message);
        if (view != null) {
            view.setText(message);
        }
    }

    public DownloadService getDownloadService() {
        // If service is not available, request it to start and wait for it.
        for (int i = 0; i < 5; i++) {
            DownloadService downloadService = DownloadServiceImpl.getInstance();
            if (downloadService != null) {
                return downloadService;
            }
            Log.w(TAG, "DownloadService not running. Attempting to start it.");
            startService(new Intent(this, DownloadServiceImpl.class));
            Util.sleepQuietly(50L);
        }
        return DownloadServiceImpl.getInstance();
    }

    protected void warnIfNetworkOrStorageUnavailable() {
        if (!Util.isExternalStoragePresent()) {
            Util.toast(this, R.string.select_album_no_sdcard);
        } else if (!Util.isOffline(this) && !Util.isNetworkConnected(this)) {
            Util.toast(this, R.string.select_album_no_network);
        }
    }

    protected synchronized ImageLoader getImageLoader() {
        if (IMAGE_LOADER == null) {
            IMAGE_LOADER = new ImageLoader(this);
        }
        return IMAGE_LOADER;
    }
    
	public synchronized static ImageLoader getStaticImageLoader(Context context) {
		if (IMAGE_LOADER == null) {
            IMAGE_LOADER = new ImageLoader(context);
        }
		return IMAGE_LOADER;
	}

	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext);
    }
	protected void downloadPlaylist(final String id, final String name, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		downloadRecursively(id, name, false, save, append, autoplay, shuffle, background, playNext);
    }
	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		ModalBackgroundTask<List<MusicDirectory.Entry>> task = new ModalBackgroundTask<List<MusicDirectory.Entry>>(this, false) {
            private static final int MAX_SONGS = 500;

            @Override
            protected List<MusicDirectory.Entry> doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
				MusicDirectory root;
				if(isDirectory)
					root = musicService.getMusicDirectory(id, name, false, SubsonicTabActivity.this, this);
				else
					root = musicService.getPlaylist(id, name, SubsonicTabActivity.this, this);
                List<MusicDirectory.Entry> songs = new LinkedList<MusicDirectory.Entry>();
                getSongsRecursively(root, songs);
                return songs;
            }

            private void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs) throws Exception {
                if (songs.size() > MAX_SONGS) {
                    return;
                }

                for (MusicDirectory.Entry song : parent.getChildren(false, true)) {
                    if (!song.isVideo()) {
                        songs.add(song);
                    }
                }
                for (MusicDirectory.Entry dir : parent.getChildren(true, false)) {
                    MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
                    getSongsRecursively(musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, SubsonicTabActivity.this, this), songs);
                }
            }

            @Override
            protected void done(List<MusicDirectory.Entry> songs) {
                DownloadService downloadService = getDownloadService();
                if (!songs.isEmpty() && downloadService != null) {
                    if (!append && !playNext) {
                        downloadService.clear();
                    }
                    warnIfNetworkOrStorageUnavailable();
					if(!background) {
						downloadService.download(songs, save, autoplay, playNext, shuffle);
						if (!append && Util.getShouldTransitionOnPlaybackPreference(SubsonicTabActivity.this)) {
							Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
						}
					}
					else {
						downloadService.downloadBackground(songs, save);
					}
                }
            }
        };

        task.execute();
    }
	
	protected void addToPlaylist(final List<MusicDirectory.Entry> songs) {
		if(songs.isEmpty()) {
			Util.toast(this, "No songs selected");
			return;
		}
		
		new LoadingTask<List<Playlist>>(this, true) {
            @Override
            protected List<Playlist> doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
				return musicService.getPlaylists(false, SubsonicTabActivity.this, this);
            }
            
            @Override
            protected void done(final List<Playlist> playlists) {
				List<String> names = new ArrayList<String>();
				for(Playlist playlist: playlists) {
					names.add(playlist.getName());
				}
				
				AlertDialog.Builder builder = new AlertDialog.Builder(SubsonicTabActivity.this);
				builder.setTitle("Add to Playlist")
					.setItems(names.toArray(new CharSequence[names.size()]), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						addToPlaylist(playlists.get(which), songs);
					}
				});
				AlertDialog dialog = builder.create();
				dialog.show();
            }
            
            @Override
            protected void error(Throwable error) {            	
            	String msg;
            	if (error instanceof OfflineException || error instanceof ServerTooOldException) {
            		msg = getErrorMessage(error);
            	} else {
            		msg = getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
            	}
            	
        		Util.toast(SubsonicTabActivity.this, msg, false);
            }
        }.execute();
	}
	
	private void addToPlaylist(final Playlist playlist, final List<MusicDirectory.Entry> songs) {		
		new SilentBackgroundTask<Void>(this) {
            @Override
            protected Void doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
				musicService.addToPlaylist(playlist.getId(), songs, SubsonicTabActivity.this, null);
                return null;
            }
            
            @Override
            protected void done(Void result) {
                Util.toast(SubsonicTabActivity.this, getResources().getString(R.string.updated_playlist, songs.size(), playlist.getName()));
            }
            
            @Override
            protected void error(Throwable error) {            	
            	String msg;
            	if (error instanceof OfflineException || error instanceof ServerTooOldException) {
            		msg = getErrorMessage(error);
            	} else {
            		msg = getResources().getString(R.string.updated_playlist_error, playlist.getName()) + " " + getErrorMessage(error);
            	}
            	
        		Util.toast(SubsonicTabActivity.this, msg, false);
            }
        }.execute();
	}

    private void setUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(handler instanceof SubsonicUncaughtExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new SubsonicUncaughtExceptionHandler(this));
        }
    }

    /**
     * Logs the stack trace of uncaught exceptions to a file on the SD card.
     */
    private static class SubsonicUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private final Thread.UncaughtExceptionHandler defaultHandler;
        private final Context context;

        private SubsonicUncaughtExceptionHandler(Context context) {
            this.context = context;
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            File file = null;
            PrintWriter printWriter = null;
            try {

                PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.thejoshwa.ultrasonic.androidapp", 0);
                file = new File(Environment.getExternalStorageDirectory(), "ultrasonic-stacktrace.txt");
                printWriter = new PrintWriter(file);
                printWriter.println("Android API level: " + Build.VERSION.SDK_INT);
                printWriter.println("UltraSonic version name: " + packageInfo.versionName);
                printWriter.println("UltraSonic version code: " + packageInfo.versionCode);
                printWriter.println();
                throwable.printStackTrace(printWriter);
                Log.i(TAG, "Stack trace written to " + file);
            } catch (Throwable x) {
                Log.e(TAG, "Failed to write stack trace to " + file, x);
            } finally {
                Util.close(printWriter);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }

            }
        }
    }

    @Override
    public void onClick(View v) {
        menuActiveViewId = v.getId();
        
        Intent intent;
        
        switch (menuActiveViewId) {
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
        
        menuDrawer.closeMenu();
    }
	
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        menuDrawer.restoreState(inState.getParcelable(STATE_MENUDRAWER));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_MENUDRAWER, menuDrawer.saveState());
        outState.putInt(STATE_ACTIVE_VIEW_ID, menuActiveViewId);
        outState.putInt(STATE_ACTIVE_POSITION, activePosition);
    }
	
    @Override
    public void onBackPressed() {
        final int drawerState = menuDrawer.getDrawerState();
        
        if (drawerState == MenuDrawer.STATE_OPEN || drawerState == MenuDrawer.STATE_OPENING) {
            menuDrawer.closeMenu();
            return;
        }

        super.onBackPressed();
    }
    
	static class SwipeDetector implements OnTouchListener {

		public static enum Action {
			LeftToRight,
			RightToLeft,
			TopToBottom,
			BottomToTop,
			None,
			Click
		}

		public static SwipeDetector Create(SubsonicTabActivity activity, final DownloadService downloadService) {
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
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: {
					downX = event.getX();
					downY = event.getY();
					return false;
				} case MotionEvent.ACTION_UP: {
					upX = event.getX();
					upY = event.getY();

					float deltaX = downX - upX;
					float deltaY = downY - upY;

					if (Math.abs(deltaX) > MIN_DISTANCE) {
						// left or right
						if (deltaX < 0) {
							downloadService.previous();
							return false;
						}
						if (deltaX > 0) {
							downloadService.next();
							return false;
						}
					} else if (Math.abs(deltaY) > MIN_DISTANCE) {
						if (deltaY < 0) {
							activity.nowPlayingHidden = true;
							activity.hideNowPlaying();
							return false;
						}
						if (deltaY > 0) {
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