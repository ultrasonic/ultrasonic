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
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.OnGestureListener;
import android.view.animation.AnimationUtils;
import android.widget.*;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.domain.RepeatMode;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.SilentBackgroundTask;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.view.AutoRepeatButton;
import com.thejoshwa.ultrasonic.androidapp.view.SongView;
import com.thejoshwa.ultrasonic.androidapp.view.VisualizerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.thejoshwa.ultrasonic.androidapp.domain.PlayerState.*;

public class DownloadActivity extends SubsonicTabActivity implements OnGestureListener {
	private static final String TAG = DownloadActivity.class.getSimpleName();
    private static final int DIALOG_SAVE_PLAYLIST = 100;
    private static final int PERCENTAGE_OF_SCREEN_FOR_SWIPE = 5;
    
    private ViewFlipper playlistFlipper;
    private TextView emptyTextView;
    private TextView songTitleTextView;
    private TextView albumTextView;
    private TextView artistTextView;
    private ImageView albumArtImageView;
    private ListView playlistView;
    private TextView positionTextView;
    private TextView durationTextView;
    private TextView statusTextView;
    private static SeekBar progressBar;
    private View pauseButton;
    private View stopButton;
    private View startButton;
    private ImageView repeatButton;
    private ImageView starImageView;
    private ScheduledExecutorService executorService;
    private DownloadFile currentPlaying;
    private Entry currentSong;
    private long currentRevision;
    private EditText playlistNameView;
    private GestureDetector gestureScanner;
    private int swipeDistance;
    private int swipeVelocity;
    private VisualizerView visualizerView;
    private boolean visualizerAvailable;
    private boolean equalizerAvailable;
    private SilentBackgroundTask<Void> onProgressChangedTask;
    
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        final WindowManager windowManager = getWindowManager();
        final Display display = windowManager.getDefaultDisplay();
        swipeDistance = (display.getWidth() + display.getHeight()) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
        swipeVelocity = (display.getWidth() + display.getHeight()) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
        gestureScanner = new GestureDetector(this);

        playlistFlipper = (ViewFlipper) findViewById(R.id.download_playlist_flipper);
        emptyTextView = (TextView) findViewById(R.id.download_empty);
        songTitleTextView = (TextView) findViewById(R.id.download_song_title);
        albumTextView = (TextView) findViewById(R.id.download_album);
        artistTextView = (TextView) findViewById(R.id.download_artist);
        albumArtImageView = (ImageView) findViewById(R.id.download_album_art_image);
        positionTextView = (TextView) findViewById(R.id.download_position);
        durationTextView = (TextView) findViewById(R.id.download_duration);
        statusTextView = (TextView) findViewById(R.id.download_status);
        progressBar = (SeekBar) findViewById(R.id.download_progress_bar);
        playlistView = (ListView) findViewById(R.id.download_list);
        final AutoRepeatButton previousButton = (AutoRepeatButton) findViewById(R.id.download_previous);
        final AutoRepeatButton nextButton = (AutoRepeatButton) findViewById(R.id.download_next);
        pauseButton = findViewById(R.id.download_pause);
        stopButton = findViewById(R.id.download_stop);
        startButton = findViewById(R.id.download_start);
        final View shuffleButton = findViewById(R.id.download_shuffle);
        repeatButton = (ImageView) findViewById(R.id.download_repeat);
        starImageView = (ImageView) findViewById(R.id.download_star);
        final LinearLayout visualizerViewLayout = (LinearLayout) findViewById(R.id.download_visualizer_view_layout);

        final View toggleListButton = findViewById(R.id.download_toggle_list);

        albumArtImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                toggleFullScreenAlbumArt();
            }
        });

        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Void>(DownloadActivity.this) {
                    @Override
                    protected Void doInBackground() throws Throwable {
                        getDownloadService().previous();
                        return null;
                    }

                    @Override
                    protected void done(final Void result) {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });
        
        previousButton.setOnRepeatListener(new Runnable() {
            public void run() {
            	int incrementTime = Util.getIncrementTime(DownloadActivity.this);
                changeProgress(-incrementTime);
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Boolean>(DownloadActivity.this) {
                    @Override
                    protected Boolean doInBackground() throws Throwable {
                        if (getDownloadService().getCurrentPlayingIndex() < getDownloadService().size() - 1) {
                            getDownloadService().next();
                            return true;
                        } else {
                            return false;
                        }
                    }

                    @Override
                    protected void done(final Boolean result) {
                        if (result) {
                            onCurrentChanged();
                            onSliderProgressChanged();
                        }
                    }
                }.execute();
            }
        });
        
        nextButton.setOnRepeatListener(new Runnable() {
            public void run() {
            	int incrementTime = Util.getIncrementTime(DownloadActivity.this);
                changeProgress(incrementTime);
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
            	new SilentBackgroundTask<Void>(DownloadActivity.this) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().pause();
						return null;
					}

					@Override
					protected void done(final Void result) {
						onCurrentChanged();
						onSliderProgressChanged();
					}
				}.execute();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
				new SilentBackgroundTask<Void>(DownloadActivity.this) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().reset();
						return null;
					}

					@Override
					protected void done(final Void result) {
						onCurrentChanged();
						onSliderProgressChanged();
					}
				}.execute();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                warnIfNetworkOrStorageUnavailable();
                
				new SilentBackgroundTask<Void>(DownloadActivity.this) {
					@Override
					protected Void doInBackground() throws Throwable {
						start();
						return null;
					}

					@Override
					protected void done(final Void result) {
						onCurrentChanged();
						onSliderProgressChanged();
					}
				}.execute();
            }
        });

        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                getDownloadService().shuffle();
                Util.toast(DownloadActivity.this, R.string.download_menu_shuffle_notification);
            }
        });

        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final RepeatMode repeatMode = getDownloadService().getRepeatMode().next();

                getDownloadService().setRepeatMode(repeatMode);
                onDownloadListChanged();

                switch (repeatMode) {
                    case OFF:
                        Util.toast(DownloadActivity.this, R.string.download_repeat_off);
                        break;
                    case ALL:
                        Util.toast(DownloadActivity.this, R.string.download_repeat_all);
                        break;
                    case SINGLE:
                        Util.toast(DownloadActivity.this, R.string.download_repeat_single);
                        break;
                    default:
                        break;
                }
            }
        });

        toggleListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                toggleFullScreenAlbumArt();
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(final SeekBar seekBar) {
				new SilentBackgroundTask<Void>(DownloadActivity.this) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().seekTo(getProgressBar().getProgress());
						return null;
					}

					@Override
					protected void done(final Void result) {
						onSliderProgressChanged();
					}
				}.execute();
				
			}
			
			@Override
			public void onStartTrackingTouch(final SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			}
		});
        		
        playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                warnIfNetworkOrStorageUnavailable();
                
                new SilentBackgroundTask<Void>(DownloadActivity.this) {
					@Override
					protected Void doInBackground() throws Throwable {
						getDownloadService().play(position);
						return null;
					}

					@Override
					protected void done(final Void result) {
						onCurrentChanged();
		                onSliderProgressChanged();
					}
				}.execute();
            }
        });
        
        registerForContextMenu(playlistView);
        
        final DownloadService downloadService = getDownloadService();
        if (downloadService != null && getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, false)) {
            warnIfNetworkOrStorageUnavailable();
            downloadService.setShufflePlayEnabled(true);
        }
        
        if (Util.isOffline(this)) {
        	starImageView.setVisibility(View.GONE);
        }
        
        starImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
            	if (currentSong == null) {
            		return;
            	}
            	
            	final boolean isStarred = currentSong.getStarred();
            	final String id = currentSong.getId();

                if (isStarred) {
                    starImageView.setImageDrawable(Util.getDrawableFromAttribute(SubsonicTabActivity.getInstance(), R.attr.star_hollow));
                    currentSong.setStarred(false);
                } else {
                    starImageView.setImageDrawable(Util.getDrawableFromAttribute(SubsonicTabActivity.getInstance(), R.attr.star_full));
                    currentSong.setStarred(true);
                }
            	
            	new Thread(new Runnable() {
            	    public void run() {
                    	final MusicService musicService = MusicServiceFactory.getMusicService(getBaseContext());
                    	
            			try {
                            if (isStarred) {
                                musicService.unstar(id, null, null, getBaseContext(), null);
                            } else {
                                musicService.star(id, null, null, getBaseContext(), null);
                            }
            			} catch (Exception e) {
							Log.e(TAG, e.getMessage(), e);
						}
            	    }
            	  }).start();
            }
        });

        visualizerAvailable = downloadService != null && downloadService.getVisualizerController() != null;
        equalizerAvailable = downloadService != null && downloadService.getEqualizerController() != null;

        final View nowPlayingMenuItem = findViewById(R.id.menu_now_playing);
        menuDrawer.setActiveView(nowPlayingMenuItem);
        
        if (visualizerAvailable) {
            visualizerView = new VisualizerView(this);
            visualizerViewLayout.addView(visualizerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            visualizerView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(final View view, final MotionEvent motionEvent) {
                    visualizerView.setActive(!visualizerView.isActive());
                    getDownloadService().setShowVisualization(visualizerView.isActive());
                    //updateButtons();
                    return true;
                }
            });
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        update();
                    }
                });
            }
        };

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(runnable, 0L, 1000L, TimeUnit.MILLISECONDS);

        final DownloadService downloadService = getDownloadService();
        if (downloadService == null || downloadService.getCurrentPlaying() == null) {
            playlistFlipper.setDisplayedChild(1);
        }

        onDownloadListChanged();
        onCurrentChanged();
        onSliderProgressChanged();
        scrollToCurrent();
        if (downloadService != null && downloadService.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (visualizerView != null) {
            visualizerView.setActive(downloadService != null && downloadService.getShowVisualization());
        }
    }

    // Scroll to current playing/downloading.
    private void scrollToCurrent() {
        if (getDownloadService() == null) {
            return;
        }

        for (int i = 0; i < playlistView.getAdapter().getCount(); i++) {
            if (currentPlaying == playlistView.getItemAtPosition(i)) {
                playlistView.setSelectionFromTop(i, 40);
                return;
            }
        }
        final DownloadFile currentDownloading = getDownloadService().getCurrentDownloading();
        for (int i = 0; i < playlistView.getAdapter().getCount(); i++) {
            if (currentDownloading == playlistView.getItemAtPosition(i)) {
                playlistView.setSelectionFromTop(i, 40);
                return;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        executorService.shutdown();
        if (visualizerView != null) {
            visualizerView.setActive(false);
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        if (id == DIALOG_SAVE_PLAYLIST) {
            final AlertDialog.Builder builder;

            final LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            final View layout = layoutInflater.inflate(R.layout.save_playlist, (ViewGroup) findViewById(R.id.save_playlist_root));
            playlistNameView = (EditText) layout.findViewById(R.id.save_playlist_name);

            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.download_playlist_title);
            builder.setMessage(R.string.download_playlist_name);
            builder.setPositiveButton(R.string.common_save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int clickId) {
                    savePlaylistInBackground(String.valueOf(playlistNameView.getText()));
                }
            });
            builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int clickId) {
                    dialog.cancel();
                }
            });
            builder.setView(layout);
            builder.setCancelable(true);

            return builder.create();
        } else {
            return super.onCreateDialog(id);
        }
    }

    @Override
    protected void onPrepareDialog(final int id, final Dialog dialog) {
        if (id == DIALOG_SAVE_PLAYLIST) {
            final String playlistName = (getDownloadService() != null) ? getDownloadService().getSuggestedPlaylistName() : null;
            if (playlistName != null) {
                playlistNameView.setText(playlistName);
            } else {
                final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                playlistNameView.setText(dateFormat.format(new Date()));
            }
        }
    }

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.nowplaying, menu);
    	super.onCreateOptionsMenu(menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem savePlaylist = menu.findItem(R.id.menu_save_playlist);
        final boolean savePlaylistEnabled = !Util.isOffline(this);
        if (savePlaylist != null) {
            savePlaylist.setEnabled(savePlaylistEnabled);
            savePlaylist.setVisible(savePlaylistEnabled);
        }

        final MenuItem screenOption = menu.findItem(R.id.menu_screen_on_off);
        final MenuItem equalizerMenuItem = menu.findItem(R.id.download_equalizer);
        final MenuItem visualizerMenuItem = menu.findItem(R.id.download_visualizer);

        if (equalizerMenuItem != null) {
            equalizerMenuItem.setEnabled(equalizerAvailable);
            equalizerMenuItem.setVisible(equalizerAvailable);
        }

        if (visualizerMenuItem != null) {
            visualizerMenuItem.setEnabled(visualizerAvailable);
            visualizerMenuItem.setVisible(visualizerAvailable);
        }

        
        final DownloadService downloadService = getDownloadService();
        
        if (downloadService != null) {
        	if (getDownloadService().getKeepScreenOn()) {
                if (screenOption != null) {
                    screenOption.setTitle(R.string.download_menu_screen_off);
                }
            } else {
                if (screenOption != null) {
                    screenOption.setTitle(R.string.download_menu_screen_on);
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view == playlistView) {
            final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            final DownloadFile downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);

            final MenuInflater menuInflater = getMenuInflater();
    		menuInflater.inflate(R.menu.nowplaying_context, menu);

            Entry song = null;

            if (downloadFile != null) {
                song = downloadFile.getSong();
            }

            if (song != null && song.getParent() == null) {
            	menu.findItem(R.id.menu_show_album).setVisible(false);
            }
            if (Util.isOffline(this)) {
                menu.findItem(R.id.menu_lyrics).setVisible(false);
                menu.findItem(R.id.menu_save_playlist).setVisible(false);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(final MenuItem menuItem) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        DownloadFile downloadFile = null;

        if (info != null) {
            downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);
        }

        return menuItemSelected(menuItem.getItemId(), downloadFile) || super.onContextItemSelected(menuItem);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        return menuItemSelected(menuItem.getItemId(), null) || super.onOptionsItemSelected(menuItem);
    }

    private boolean menuItemSelected(final int menuItemId, final DownloadFile song) {
        switch (menuItemId) {
            case R.id.menu_show_album:
                Intent intent = new Intent(this, SelectAlbumActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, song.getSong().getParent());
                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, song.getSong().getAlbum());
                Util.startActivityWithoutTransition(this, intent);
                return true;
            case R.id.menu_lyrics:
                intent = new Intent(this, LyricsActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_ARTIST, song.getSong().getArtist());
                intent.putExtra(Constants.INTENT_EXTRA_NAME_TITLE, song.getSong().getTitle());
                Util.startActivityWithoutTransition(this, intent);
                return true;
            case R.id.menu_remove:
                getDownloadService().remove(song);
                onDownloadListChanged();
                return true;
            case R.id.menu_remove_all:
                getDownloadService().setShufflePlayEnabled(false);
                getDownloadService().clear();
                onDownloadListChanged();
                return true;
            case R.id.menu_screen_on_off:
                if (getDownloadService().getKeepScreenOn()) {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            		getDownloadService().setKeepScreenOn(false);
            	} else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            		getDownloadService().setKeepScreenOn(true);
            	}
                return true;
            case R.id.menu_shuffle:
                getDownloadService().shuffle();
                Util.toast(this, R.string.download_menu_shuffle_notification);
                return true;
            case R.id.menu_save_playlist:
                showDialog(DIALOG_SAVE_PLAYLIST);
                return true;
            case R.id.download_equalizer:
            	startActivity(new Intent(DownloadActivity.this, EqualizerActivity.class));
            	return true;
            case R.id.download_visualizer:
                final boolean active = !visualizerView.isActive();
                visualizerView.setActive(active);
                getDownloadService().setShowVisualization(visualizerView.isActive());
                Util.toast(DownloadActivity.this, active ? R.string.download_visualizer_on : R.string.download_visualizer_off);
            	return true;
            case R.id.download_jukebox:
                final boolean jukeboxEnabled = !getDownloadService().isJukeboxEnabled();
                getDownloadService().setJukeboxEnabled(jukeboxEnabled);
                Util.toast(DownloadActivity.this, jukeboxEnabled ? R.string.download_jukebox_on : R.string.download_jukebox_off, false);
            	return true;            	
            default:
                return false;
        }
    }

    private void update() {
        if (getDownloadService() == null) {
            return;
        }

        if (currentRevision != getDownloadService().getDownloadListUpdateRevision()) {
            onDownloadListChanged();
        }

        if (currentPlaying != getDownloadService().getCurrentPlaying()) {
            onCurrentChanged();
        }

        onSliderProgressChanged();
    }

    private void savePlaylistInBackground(final String playlistName) {
        Util.toast(DownloadActivity.this, getResources().getString(R.string.download_playlist_saving, playlistName));
        getDownloadService().setSuggestedPlaylistName(playlistName);
        new SilentBackgroundTask<Void>(this) {
            @Override
            protected Void doInBackground() throws Throwable {
                final List<MusicDirectory.Entry> entries = new LinkedList<MusicDirectory.Entry>();
                for (final DownloadFile downloadFile : getDownloadService().getSongs()) {
                    entries.add(downloadFile.getSong());
                }
                final MusicService musicService = MusicServiceFactory.getMusicService(DownloadActivity.this);
                musicService.createPlaylist(null, playlistName, entries, DownloadActivity.this, null);
                return null;
            }

            @Override
            protected void done(final Void result) {
                Util.toast(DownloadActivity.this, R.string.download_playlist_done);
            }

            @Override
            protected void error(final Throwable error) {
                final String msg = getResources().getString(R.string.download_playlist_error) + " " + getErrorMessage(error);
                Util.toast(DownloadActivity.this, msg);
            }
        }.execute();
    }

    private void toggleFullScreenAlbumArt() {
    	scrollToCurrent();
        if (playlistFlipper.getDisplayedChild() == 1) {
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.push_down_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.push_down_out));
            playlistFlipper.setDisplayedChild(0);
        } else {
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.push_up_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.push_up_out));
            playlistFlipper.setDisplayedChild(1);
        }
    }

    private void start() {
        final DownloadService service = getDownloadService();
        final PlayerState state = service.getPlayerState();

        if (state == PAUSED || state == COMPLETED || state == STOPPED) {
            service.start();
        } else if (state == IDLE) {
            warnIfNetworkOrStorageUnavailable();

            final int current = service.getCurrentPlayingIndex();

            if (current == -1) {
                service.play(0);
            } else {
                service.play(current);
            }
        }
    }
	
    private void onDownloadListChanged() {
        final DownloadService downloadService = getDownloadService();
        if (downloadService == null) {
            return;
        }

		final List<DownloadFile> list = downloadService.getSongs();

		emptyTextView.setText(R.string.download_empty);
		playlistView.setAdapter(new SongListAdapter(list));
        emptyTextView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        currentRevision = downloadService.getDownloadListUpdateRevision();

        switch (downloadService.getRepeatMode()) {
        	case OFF:
        		repeatButton.setImageDrawable(Util.getDrawableFromAttribute(this, R.attr.media_repeat_off));
        		break;
        	case ALL:
        		repeatButton.setImageDrawable(Util.getDrawableFromAttribute(this, R.attr.media_repeat_all));
        		break;
        	case SINGLE:
        		repeatButton.setImageDrawable(Util.getDrawableFromAttribute(this, R.attr.media_repeat_single));
        		break;
        	default:
        		break;
        }
    }

    private void onCurrentChanged() {
        if (getDownloadService() == null) {
            return;
        }

        currentPlaying = getDownloadService().getCurrentPlaying();
        if (currentPlaying != null) {
            currentSong = currentPlaying.getSong();
            final Drawable starDrawable = currentSong.getStarred() ? Util.getDrawableFromAttribute(SubsonicTabActivity.getInstance(), R.attr.star_full) : Util.getDrawableFromAttribute(SubsonicTabActivity.getInstance(), R.attr.star_hollow);
            starImageView.setImageDrawable(starDrawable);
            songTitleTextView.setText(currentSong.getTitle());
            albumTextView.setText(currentSong.getAlbum());
            artistTextView.setText(currentSong.getArtist());
            getImageLoader().loadImage(albumArtImageView, currentSong, true, true);
        } else {
        	currentSong = null;
            songTitleTextView.setText(null);
            albumTextView.setText(null);
            artistTextView.setText(null);
            getImageLoader().loadImage(albumArtImageView, null, true, false);
        }
    }

	private void onSliderProgressChanged() {
		if (getDownloadService() == null || onProgressChangedTask != null) {
			return;
		}

		onProgressChangedTask = new SilentBackgroundTask<Void>(this) {
			DownloadService downloadService;
			boolean isJukeboxEnabled;
			int millisPlayed;
			Integer duration;
			PlayerState playerState;

			@Override
			protected Void doInBackground() throws Throwable {
				downloadService = getDownloadService();
				isJukeboxEnabled = downloadService.isJukeboxEnabled();
				millisPlayed = Math.max(0, downloadService.getPlayerPosition());
				duration = downloadService.getPlayerDuration();
				playerState = getDownloadService().getPlayerState();
				return null;
			}

			@Override
			protected void done(final Void result) {
				if (currentPlaying != null) {
					final int millisTotal = duration == null ? 0 : duration;

					positionTextView.setText(Util.formatDuration(millisPlayed / 1000));
					durationTextView.setText(Util.formatDuration(millisTotal / 1000));
					progressBar.setMax(millisTotal == 0 ? 100 : millisTotal); // Work-around for apparent bug.
					progressBar.setProgress(millisPlayed);
					progressBar.setEnabled(currentPlaying.isWorkDone() || isJukeboxEnabled);
				} else {
					positionTextView.setText(R.string.util_zero_time);
					durationTextView.setText(R.string.util_no_time);
					progressBar.setProgress(0);
					progressBar.setMax(0);
					progressBar.setEnabled(false);
				}

                switch (playerState) {
                    case DOWNLOADING:
                        final long bytes = currentPlaying != null ? currentPlaying.getPartialFile().length() : 0;
                        statusTextView.setText(getResources().getString(
                                R.string.download_playerstate_downloading,
                                Util.formatLocalizedBytes(bytes,
                                        DownloadActivity.this)));
                        break;
                    case PREPARING:
                        statusTextView
                                .setText(R.string.download_playerstate_buffering);
                        break;
                    case STARTED:
                    	final DownloadService downloadService = getDownloadService();
                    	
                        if (downloadService != null && downloadService.isShufflePlayEnabled()) {
                            statusTextView
                                    .setText(R.string.download_playerstate_playing_shuffle);
                        } else {
                            statusTextView.setText(null);
                        }
                        break;
                    default:
                        statusTextView.setText(null);
                        break;
                    case IDLE:
                        break;
                    case PREPARED:
                        break;
                    case STOPPED:
                        break;
                    case PAUSED:
                        break;
                    case COMPLETED:
                        break;
                }

				switch (playerState) {
				case STARTED:
					pauseButton.setVisibility(View.VISIBLE);
					stopButton.setVisibility(View.GONE);
					startButton.setVisibility(View.GONE);
					break;
				case DOWNLOADING:
				case PREPARING:
					pauseButton.setVisibility(View.GONE);
					stopButton.setVisibility(View.VISIBLE);
					startButton.setVisibility(View.GONE);
					break;
				default:
					pauseButton.setVisibility(View.GONE);
					stopButton.setVisibility(View.GONE);
					startButton.setVisibility(View.VISIBLE);
					break;
				}

				onProgressChangedTask = null;
			}
		};
		onProgressChangedTask.execute();
	}
	
	private void changeProgress(final int ms) {
		final DownloadService downloadService = getDownloadService();
		if(downloadService == null) {
			return;
		}

		new SilentBackgroundTask<Void>(this) {
			int msPlayed;
			Integer duration;
			int seekTo;

			@Override
            protected Void doInBackground() throws Throwable {
				msPlayed = Math.max(0, downloadService.getPlayerPosition());
				duration = downloadService.getPlayerDuration();

				final int msTotal = duration == null ? 0 : duration;
                seekTo = msPlayed + ms > msTotal ? msTotal : msPlayed + ms;
				downloadService.seekTo(seekTo);
                return null;
            }
            
            @Override
            protected void done(final Void result) {
				progressBar.setProgress(seekTo);
			}
		}.execute();
	}
    
    private class SongListAdapter extends ArrayAdapter<DownloadFile> {
        public SongListAdapter(final List<DownloadFile> entries) {
            super(DownloadActivity.this, android.R.layout.simple_list_item_1, entries);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final SongView view;
            view = convertView != null && convertView instanceof SongView ? (SongView) convertView : new SongView(DownloadActivity.this);
            final DownloadFile downloadFile = getItem(position);
            view.setSong(downloadFile.getSong(), false);
            return view;
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        return gestureScanner.onTouchEvent(me);
    }

	@Override
	public boolean onDown(final MotionEvent me) {
		return false;
	}

	@Override
	public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {

        final DownloadService downloadService = getDownloadService();
        if (downloadService == null) {
            return false;
        }

		// Right to Left swipe
		if (e1.getX() - e2.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            warnIfNetworkOrStorageUnavailable();
            if (downloadService.getCurrentPlayingIndex() < downloadService.size() - 1) {
                downloadService.next();
                onCurrentChanged();
                onSliderProgressChanged();
            }
			return true;
		}

		// Left to Right swipe
        if (e2.getX() - e1.getX() > swipeDistance && Math.abs(velocityX) > swipeVelocity) {
            warnIfNetworkOrStorageUnavailable();
            downloadService.previous();
            onCurrentChanged();
            onSliderProgressChanged();
			return true;
		}

        // Top to Bottom swipe
         if (e2.getY() - e1.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
             warnIfNetworkOrStorageUnavailable();
             downloadService.seekTo(downloadService.getPlayerPosition() + 30000);
             onSliderProgressChanged();
             return true;
         }

        // Bottom to Top swipe
        if (e1.getY() - e2.getY() > swipeDistance && Math.abs(velocityY) > swipeVelocity) {
            warnIfNetworkOrStorageUnavailable();
            downloadService.seekTo(downloadService.getPlayerPosition() - 8000);
            onSliderProgressChanged();
            return true;
        }

        return false;
    }

	@Override
	public void onLongPress(final MotionEvent e) {
	}

	@Override
	public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
		return false;
	}

	@Override
	public void onShowPress(final MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(final MotionEvent e) {
		return false;
	}
	
	public static SeekBar getProgressBar() {
		return progressBar;
	}
}
