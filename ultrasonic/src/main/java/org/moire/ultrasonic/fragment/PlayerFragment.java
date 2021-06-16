package org.moire.ultrasonic.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.navigation.Navigation;

import com.mobeta.android.dslv.DragSortListView;

import org.jetbrains.annotations.NotNull;
import org.koin.java.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker;
import org.moire.ultrasonic.subsonic.ShareHandler;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.SilentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.AutoRepeatButton;
import org.moire.ultrasonic.view.SongListAdapter;
import org.moire.ultrasonic.view.VisualizerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import kotlin.Lazy;
import timber.log.Timber;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static org.koin.java.KoinJavaComponent.inject;
import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.STOPPED;

/**
 * Contains the Music Player screen of Ultrasonic with playback controls and the playlist
 */
public class PlayerFragment extends Fragment implements GestureDetector.OnGestureListener {

    private static final int PERCENTAGE_OF_SCREEN_FOR_SWIPE = 5;

    private ViewFlipper playlistFlipper;
    private TextView emptyTextView;
    private TextView songTitleTextView;
    private TextView albumTextView;
    private TextView artistTextView;
    private ImageView albumArtImageView;
    private DragSortListView playlistView;
    private TextView positionTextView;
    private TextView downloadTrackTextView;
    private TextView downloadTotalDurationTextView;
    private TextView durationTextView;
    private static SeekBar progressBar; // TODO: Refactor this to not be static
    private View pauseButton;
    private View stopButton;
    private View startButton;
    private ImageView repeatButton;
    private ScheduledExecutorService executorService;
    private DownloadFile currentPlaying;
    private MusicDirectory.Entry currentSong;
    private long currentRevision;
    private EditText playlistNameView;
    private GestureDetector gestureScanner;
    private int swipeDistance;
    private int swipeVelocity;
    private VisualizerView visualizerView;
    private boolean jukeboxAvailable;
    private SilentBackgroundTask<Void> onProgressChangedTask;
    LinearLayout visualizerViewLayout;
    private MenuItem starMenuItem;
    private ImageView fiveStar1ImageView;
    private ImageView fiveStar2ImageView;
    private ImageView fiveStar3ImageView;
    private ImageView fiveStar4ImageView;
    private ImageView fiveStar5ImageView;
    private boolean useFiveStarRating;
    private Drawable hollowStar;
    private Drawable fullStar;
    private CancellationToken cancellationToken;

    private boolean isEqualizerAvailable;
    private boolean isVisualizerAvailable;

    private final Lazy<NetworkAndStorageChecker> networkAndStorageChecker = inject(NetworkAndStorageChecker.class);
    private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
    private final Lazy<ShareHandler> shareHandler = inject(ShareHandler.class);
    private final Lazy<ImageLoaderProvider> imageLoaderProvider = inject(ImageLoaderProvider.class);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.current_playing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        FragmentTitle.Companion.setTitle(this, R.string.common_appname);

        final WindowManager windowManager = getActivity().getWindowManager();
        final Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        setHasOptionsMenu(true);

        FeatureStorage features = KoinJavaComponent.get(FeatureStorage.class);
        useFiveStarRating = features.isFeatureEnabled(Feature.FIVE_STAR_RATING);

        swipeDistance = (width + height) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100;
        swipeVelocity = swipeDistance;
        gestureScanner = new GestureDetector(getContext(), this);

        playlistFlipper = view.findViewById(R.id.current_playing_playlist_flipper);
        emptyTextView = view.findViewById(R.id.playlist_empty);
        songTitleTextView = view.findViewById(R.id.current_playing_song);
        albumTextView = view.findViewById(R.id.current_playing_album);
        artistTextView = view.findViewById(R.id.current_playing_artist);
        albumArtImageView = view.findViewById(R.id.current_playing_album_art_image);
        positionTextView = view.findViewById(R.id.current_playing_position);
        downloadTrackTextView = view.findViewById(R.id.current_playing_track);
        downloadTotalDurationTextView = view.findViewById(R.id.current_total_duration);
        durationTextView = view.findViewById(R.id.current_playing_duration);
        progressBar = view.findViewById(R.id.current_playing_progress_bar);
        playlistView = view.findViewById(R.id.playlist_view);
        final AutoRepeatButton previousButton = view.findViewById(R.id.button_previous);
        final AutoRepeatButton nextButton = view.findViewById(R.id.button_next);
        pauseButton = view.findViewById(R.id.button_pause);
        stopButton = view.findViewById(R.id.button_stop);
        startButton = view.findViewById(R.id.button_start);
        final View shuffleButton = view.findViewById(R.id.button_shuffle);
        repeatButton = view.findViewById(R.id.button_repeat);

        visualizerViewLayout = view.findViewById(R.id.current_playing_visualizer_layout);

        LinearLayout ratingLinearLayout = view.findViewById(R.id.song_rating);
        fiveStar1ImageView = view.findViewById(R.id.song_five_star_1);
        fiveStar2ImageView = view.findViewById(R.id.song_five_star_2);
        fiveStar3ImageView = view.findViewById(R.id.song_five_star_3);
        fiveStar4ImageView = view.findViewById(R.id.song_five_star_4);
        fiveStar5ImageView = view.findViewById(R.id.song_five_star_5);

        if (!useFiveStarRating) ratingLinearLayout.setVisibility(View.GONE);

        hollowStar = Util.getDrawableFromAttribute(view.getContext(), R.attr.star_hollow);
        fullStar = Util.getDrawableFromAttribute(getContext(), R.attr.star_full);

        fiveStar1ImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view) {
                setSongRating(1);
            }
        });
        fiveStar2ImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view) {
                setSongRating(2);
            }
        });
        fiveStar3ImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view) {
                setSongRating(3);
            }
        });
        fiveStar4ImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view) {
                setSongRating(4);
            }
        });
        fiveStar5ImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view) {
                setSongRating(5);
            }
        });

        albumArtImageView.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View view, MotionEvent me)
            {
                return gestureScanner.onTouchEvent(me);
            }
        });

        albumArtImageView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                toggleFullScreenAlbumArt();
            }
        });

        previousButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().previous();
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });

        previousButton.setOnRepeatListener(new Runnable()
        {
            @Override
            public void run()
            {
                int incrementTime = Util.getIncrementTime();
                changeProgress(-incrementTime);
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Boolean>(getActivity())
                {
                    @Override
                    protected Boolean doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().next();
                        return true;
                    }

                    @Override
                    protected void done(final Boolean result)
                    {
                        if (result)
                        {
                            onCurrentChanged();
                            onSliderProgressChanged();
                        }
                    }
                }.execute();
            }
        });

        nextButton.setOnRepeatListener(new Runnable()
        {
            @Override
            public void run()
            {
                int incrementTime = Util.getIncrementTime();
                changeProgress(incrementTime);
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().pause();
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().reset();
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        start();
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });

        shuffleButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                mediaPlayerControllerLazy.getValue().shuffle();
                Util.toast(getActivity(), R.string.download_menu_shuffle_notification);
            }
        });

        repeatButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View view)
            {
                final RepeatMode repeatMode = mediaPlayerControllerLazy.getValue().getRepeatMode().next();

                mediaPlayerControllerLazy.getValue().setRepeatMode(repeatMode);
                onDownloadListChanged();

                switch (repeatMode)
                {
                    case OFF:
                        Util.toast(getContext(), R.string.download_repeat_off);
                        break;
                    case ALL:
                        Util.toast(getContext(), R.string.download_repeat_all);
                        break;
                    case SINGLE:
                        Util.toast(getContext(), R.string.download_repeat_single);
                        break;
                    default:
                        break;
                }
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onStopTrackingTouch(final SeekBar seekBar)
            {
                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().seekTo(getProgressBar().getProgress());
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onSliderProgressChanged();
                    }
                }.execute();
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar)
            {
            }

            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser)
            {
            }
        });

        playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id)
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();

                new SilentBackgroundTask<Void>(getActivity())
                {
                    @Override
                    protected Void doInBackground()
                    {
                        mediaPlayerControllerLazy.getValue().play(position);
                        return null;
                    }

                    @Override
                    protected void done(final Void result)
                    {
                        onCurrentChanged();
                        onSliderProgressChanged();
                    }
                }.execute();
            }
        });

        registerForContextMenu(playlistView);

        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
        if (mediaPlayerController != null && getArguments() != null && getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false))
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
            mediaPlayerController.setShufflePlayEnabled(true);
        }

        visualizerViewLayout.setVisibility(View.GONE);
        VisualizerController.get().observe(getActivity(), new Observer<VisualizerController>() {
            @Override
            public void onChanged(VisualizerController visualizerController) {
                if (visualizerController != null) {
                    Timber.d("VisualizerController Observer.onChanged received controller");
                    visualizerView = new VisualizerView(getContext());
                    visualizerViewLayout.addView(visualizerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

                    if (!visualizerView.isActive())
                    {
                        visualizerViewLayout.setVisibility(View.GONE);
                    }
                    else
                    {
                        visualizerViewLayout.setVisibility(View.VISIBLE);
                    }

                    visualizerView.setOnTouchListener(new View.OnTouchListener()
                    {
                        @Override
                        public boolean onTouch(final View view, final MotionEvent motionEvent)
                        {
                            visualizerView.setActive(!visualizerView.isActive());
                            mediaPlayerControllerLazy.getValue().setShowVisualization(visualizerView.isActive());
                            return true;
                        }
                    });
                    isVisualizerAvailable = true;
                } else {
                    Timber.d("VisualizerController Observer.onChanged has no controller");
                    visualizerViewLayout.setVisibility(View.GONE);
                    isVisualizerAvailable = false;
                }
            }
        });

        EqualizerController.get().observe(getActivity(), new Observer<EqualizerController>() {
            @Override
            public void onChanged(EqualizerController equalizerController) {
                if (equalizerController != null) {
                    Timber.d("EqualizerController Observer.onChanged received controller");
                    isEqualizerAvailable = true;
                } else {
                    Timber.d("EqualizerController Observer.onChanged has no controller");
                    isEqualizerAvailable = false;
                }
            }
        });

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
                    jukeboxAvailable = (mediaPlayerController != null) && (mediaPlayerController.isJukeboxAvailable());
                }
                catch (Exception e)
                {
                    Timber.e(e);
                }
            }
        }).start();

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureScanner.onTouchEvent(event);
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();

        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

        if (mediaPlayerController == null || mediaPlayerController.getCurrentPlaying() == null)
        {
            playlistFlipper.setDisplayedChild(1);
        }
        else
        {
            // Download list and Album art must be updated when Resumed
            onDownloadListChanged();
            onCurrentChanged();
        }


        final Handler handler = new Handler();
        final Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        update(cancellationToken);
                    }
                });
            }
        };

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(runnable, 0L, 250L, TimeUnit.MILLISECONDS);

        if (mediaPlayerController != null && mediaPlayerController.getKeepScreenOn())
        {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else
        {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (visualizerView != null)
        {
            visualizerView.setActive(mediaPlayerController != null && mediaPlayerController.getShowVisualization());
        }

        getActivity().invalidateOptionsMenu();
    }

    // Scroll to current playing/downloading.
    private void scrollToCurrent()
    {
        ListAdapter adapter = playlistView.getAdapter();

        if (adapter != null)
        {
            int count = adapter.getCount();

            for (int i = 0; i < count; i++)
            {
                if (currentPlaying == playlistView.getItemAtPosition(i))
                {
                    playlistView.smoothScrollToPositionFromTop(i, 40);
                    return;
                }
            }

            final DownloadFile currentDownloading = mediaPlayerControllerLazy.getValue().getCurrentDownloading();
            for (int i = 0; i < count; i++)
            {
                if (currentDownloading == playlistView.getItemAtPosition(i))
                {
                    playlistView.smoothScrollToPositionFromTop(i, 40);
                    return;
                }
            }
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        executorService.shutdown();

        if (visualizerView != null)
        {
            visualizerView.setActive(false);
        }
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.nowplaying, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final MenuItem screenOption = menu.findItem(R.id.menu_item_screen_on_off);
        final MenuItem jukeboxOption = menu.findItem(R.id.menu_item_jukebox);
        final MenuItem equalizerMenuItem = menu.findItem(R.id.menu_item_equalizer);
        final MenuItem visualizerMenuItem = menu.findItem(R.id.menu_item_visualizer);
        final MenuItem shareMenuItem = menu.findItem(R.id.menu_item_share);
        starMenuItem = menu.findItem(R.id.menu_item_star);
        MenuItem bookmarkMenuItem = menu.findItem(R.id.menu_item_bookmark_set);
        MenuItem bookmarkRemoveMenuItem = menu.findItem(R.id.menu_item_bookmark_delete);


        if (ActiveServerProvider.Companion.isOffline())
        {
            if (shareMenuItem != null)
            {
                shareMenuItem.setVisible(false);
            }

            if (starMenuItem != null)
            {
                starMenuItem.setVisible(false);
            }

            if (bookmarkMenuItem != null)
            {
                bookmarkMenuItem.setVisible(false);
            }

            if (bookmarkRemoveMenuItem != null)
            {
                bookmarkRemoveMenuItem.setVisible(false);
            }
        }

        if (equalizerMenuItem != null)
        {
            equalizerMenuItem.setEnabled(isEqualizerAvailable);
            equalizerMenuItem.setVisible(isEqualizerAvailable);
        }

        if (visualizerMenuItem != null)
        {
            visualizerMenuItem.setEnabled(isVisualizerAvailable);
            visualizerMenuItem.setVisible(isVisualizerAvailable);
        }

        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

        if (mediaPlayerController != null)
        {
            DownloadFile downloadFile = mediaPlayerController.getCurrentPlaying();

            if (downloadFile != null)
            {
                currentSong = downloadFile.getSong();
            }

            if (useFiveStarRating) starMenuItem.setVisible(false);

            if (currentSong != null)
            {
                if (starMenuItem != null)
                {
                    starMenuItem.setIcon(currentSong.getStarred() ? fullStar : hollowStar);
                }
            }
            else
            {
                if (starMenuItem != null)
                {
                    starMenuItem.setIcon(hollowStar);
                }
            }


            if (mediaPlayerController.getKeepScreenOn())
            {
                if (screenOption != null)
                {
                    screenOption.setTitle(R.string.download_menu_screen_off);
                }
            }
            else
            {
                if (screenOption != null)
                {
                    screenOption.setTitle(R.string.download_menu_screen_on);
                }
            }

            if (jukeboxOption != null)
            {
                jukeboxOption.setEnabled(jukeboxAvailable);
                jukeboxOption.setVisible(jukeboxAvailable);

                if (mediaPlayerController.isJukeboxEnabled())
                {
                    jukeboxOption.setTitle(R.string.download_menu_jukebox_off);
                }
                else
                {
                    jukeboxOption.setTitle(R.string.download_menu_jukebox_on);
                }
            }
        }
    }

    @Override
    public void onCreateContextMenu(final @NotNull ContextMenu menu, final @NotNull View view, final ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (view == playlistView)
        {
            final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            final DownloadFile downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);

            final MenuInflater menuInflater = getActivity().getMenuInflater();
            menuInflater.inflate(R.menu.nowplaying_context, menu);

            MusicDirectory.Entry song = null;

            if (downloadFile != null)
            {
                song = downloadFile.getSong();
            }

            if (song != null && song.getParent() == null)
            {
                MenuItem menuItem = menu.findItem(R.id.menu_show_album);

                if (menuItem != null)
                {
                    menuItem.setVisible(false);
                }
            }

            if (ActiveServerProvider.Companion.isOffline() || !Util.getShouldUseId3Tags())
            {
                MenuItem menuItem = menu.findItem(R.id.menu_show_artist);

                if (menuItem != null)
                {
                    menuItem.setVisible(false);
                }
            }

            if (ActiveServerProvider.Companion.isOffline())
            {
                MenuItem menuItem = menu.findItem(R.id.menu_lyrics);

                if (menuItem != null)
                {
                    menuItem.setVisible(false);
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(final MenuItem menuItem)
    {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        DownloadFile downloadFile = null;

        if (info != null)
        {
            downloadFile = (DownloadFile) playlistView.getItemAtPosition(info.position);
        }

        return menuItemSelected(menuItem.getItemId(), downloadFile) || super.onContextItemSelected(menuItem);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return menuItemSelected(item.getItemId(), null) || super.onOptionsItemSelected(item);
    }

    private boolean menuItemSelected(final int menuItemId, final DownloadFile song)
    {
        MusicDirectory.Entry entry = null;
        Bundle bundle;

        if (song != null)
        {
            entry = song.getSong();
        }

        if (menuItemId == R.id.menu_show_artist) {
            if (entry == null) {
                return false;
            }

            if (Util.getShouldUseId3Tags()) {
                bundle = new Bundle();
                bundle.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
                bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
                bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getArtistId());
                bundle.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
                Navigation.findNavController(getView()).navigate(R.id.playerToSelectAlbum, bundle);
            }

            return true;
        } else if (menuItemId == R.id.menu_show_album) {
            if (entry == null) {
                return false;
            }

            String albumId = Util.getShouldUseId3Tags() ? entry.getAlbumId() : entry.getParent();
            bundle = new Bundle();
            bundle.putString(Constants.INTENT_EXTRA_NAME_ID, albumId);
            bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getAlbum());
            bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getParent());
            bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, true);
            Navigation.findNavController(getView()).navigate(R.id.playerToSelectAlbum, bundle);
            return true;
        } else if (menuItemId == R.id.menu_lyrics) {
            if (entry == null) {
                return false;
            }

            bundle = new Bundle();
            bundle.putString(Constants.INTENT_EXTRA_NAME_ARTIST, entry.getArtist());
            bundle.putString(Constants.INTENT_EXTRA_NAME_TITLE, entry.getTitle());
            Navigation.findNavController(getView()).navigate(R.id.playerToLyrics, bundle);
            return true;
        } else if (menuItemId == R.id.menu_remove) {
            mediaPlayerControllerLazy.getValue().remove(song);
            onDownloadListChanged();
            return true;
        } else if (menuItemId == R.id.menu_item_screen_on_off) {
            if (mediaPlayerControllerLazy.getValue().getKeepScreenOn()) {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mediaPlayerControllerLazy.getValue().setKeepScreenOn(false);
            } else {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mediaPlayerControllerLazy.getValue().setKeepScreenOn(true);
            }
            return true;
        } else if (menuItemId == R.id.menu_shuffle) {
            mediaPlayerControllerLazy.getValue().shuffle();
            Util.toast(getContext(), R.string.download_menu_shuffle_notification);
            return true;
        } else if (menuItemId == R.id.menu_item_equalizer) {
            Navigation.findNavController(getView()).navigate(R.id.playerToEqualizer);
            return true;
        } else if (menuItemId == R.id.menu_item_visualizer) {
            final boolean active = !visualizerView.isActive();
            visualizerView.setActive(active);

            if (!visualizerView.isActive()) {
                visualizerViewLayout.setVisibility(View.GONE);
            } else {
                visualizerViewLayout.setVisibility(View.VISIBLE);
            }

            mediaPlayerControllerLazy.getValue().setShowVisualization(visualizerView.isActive());
            Util.toast(getContext(), active ? R.string.download_visualizer_on : R.string.download_visualizer_off);
            return true;
        } else if (menuItemId == R.id.menu_item_jukebox) {
            final boolean jukeboxEnabled = !mediaPlayerControllerLazy.getValue().isJukeboxEnabled();
            mediaPlayerControllerLazy.getValue().setJukeboxEnabled(jukeboxEnabled);
            Util.toast(getContext(), jukeboxEnabled ? R.string.download_jukebox_on : R.string.download_jukebox_off, false);
            return true;
        } else if (menuItemId == R.id.menu_item_toggle_list) {
            toggleFullScreenAlbumArt();
            return true;
        } else if (menuItemId == R.id.menu_item_clear_playlist) {
            mediaPlayerControllerLazy.getValue().setShufflePlayEnabled(false);
            mediaPlayerControllerLazy.getValue().clear();
            onDownloadListChanged();
            return true;
        } else if (menuItemId == R.id.menu_item_save_playlist) {
            if (mediaPlayerControllerLazy.getValue().getPlaylistSize() > 0) {
                showSavePlaylistDialog();
            }
            return true;
        } else if (menuItemId == R.id.menu_item_star) {
            if (currentSong == null) {
                return true;
            }

            final boolean isStarred = currentSong.getStarred();
            final String id = currentSong.getId();

            if (isStarred) {
                starMenuItem.setIcon(hollowStar);
                currentSong.setStarred(false);
            } else {
                starMenuItem.setIcon(fullStar);
                currentSong.setStarred(true);
            }

            // Code is duplicated with MediaPlayerController:457 FIXME: There should be a better way
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final MusicService musicService = MusicServiceFactory.getMusicService();

                    try {
                        if (isStarred) {
                            musicService.unstar(id, null, null);
                        } else {
                            musicService.star(id, null, null);
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }).start();

            return true;
        } else if (menuItemId == R.id.menu_item_bookmark_set) {
            if (currentSong == null) {
                return true;
            }

            final String songId = currentSong.getId();
            final int playerPosition = mediaPlayerControllerLazy.getValue().getPlayerPosition();

            currentSong.setBookmarkPosition(playerPosition);

            String bookmarkTime = Util.formatTotalDuration(playerPosition, true);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final MusicService musicService = MusicServiceFactory.getMusicService();

                    try {
                        musicService.createBookmark(songId, playerPosition);
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }).start();

            String msg = getResources().getString(R.string.download_bookmark_set_at_position, bookmarkTime);

            Util.toast(getContext(), msg);

            return true;
        } else if (menuItemId == R.id.menu_item_bookmark_delete) {
            if (currentSong == null) {
                return true;
            }

            final String bookmarkSongId = currentSong.getId();
            currentSong.setBookmarkPosition(0);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final MusicService musicService = MusicServiceFactory.getMusicService();

                    try {
                        musicService.deleteBookmark(bookmarkSongId);
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }).start();

            Util.toast(getContext(), R.string.download_bookmark_removed);

            return true;
        } else if (menuItemId == R.id.menu_item_share) {
            MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
            List<MusicDirectory.Entry> entries = new ArrayList<>();

            if (mediaPlayerController != null) {
                List<DownloadFile> downloadServiceSongs = mediaPlayerController.getPlayList();

                if (downloadServiceSongs != null) {
                    for (DownloadFile downloadFile : downloadServiceSongs) {
                        if (downloadFile != null) {
                            MusicDirectory.Entry playlistEntry = downloadFile.getSong();

                            if (playlistEntry != null) {
                                entries.add(playlistEntry);
                            }
                        }
                    }
                }
            }

            shareHandler.getValue().createShare(this, entries, null, cancellationToken);
            return true;
        }
        return false;
    }

    private void update(CancellationToken cancel)
    {
        if (cancel.isCancellationRequested()) return;

        MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
        if (mediaPlayerController == null)
        {
            return;
        }

        if (currentRevision != mediaPlayerController.getPlayListUpdateRevision())
        {
            onDownloadListChanged();
        }

        if (currentPlaying != mediaPlayerController.getCurrentPlaying())
        {
            onCurrentChanged();
        }

        onSliderProgressChanged();
        getActivity().invalidateOptionsMenu();
    }

    private void savePlaylistInBackground(final String playlistName)
    {
        Util.toast(getContext(), getResources().getString(R.string.download_playlist_saving, playlistName));
        mediaPlayerControllerLazy.getValue().setSuggestedPlaylistName(playlistName);
        new SilentBackgroundTask<Void>(getActivity())
        {
            @Override
            protected Void doInBackground() throws Throwable
            {
                final List<MusicDirectory.Entry> entries = new LinkedList<>();
                for (final DownloadFile downloadFile : mediaPlayerControllerLazy.getValue().getPlayList())
                {
                    entries.add(downloadFile.getSong());
                }
                final MusicService musicService = MusicServiceFactory.getMusicService();
                musicService.createPlaylist(null, playlistName, entries);
                return null;
            }

            @Override
            protected void done(final Void result)
            {
                Util.toast(getContext(), R.string.download_playlist_done);
            }

            @Override
            protected void error(final Throwable error)
            {
                Timber.e(error, "Exception has occurred in savePlaylistInBackground");
                final String msg = String.format("%s %s", getResources().getString(R.string.download_playlist_error), getErrorMessage(error));
                Util.toast(getContext(), msg);
            }
        }.execute();
    }

    private void toggleFullScreenAlbumArt()
    {
        if (playlistFlipper.getDisplayedChild() == 1)
        {
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.push_down_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.push_down_out));
            playlistFlipper.setDisplayedChild(0);
        }
        else
        {
            playlistFlipper.setInAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.push_up_in));
            playlistFlipper.setOutAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.push_up_out));
            playlistFlipper.setDisplayedChild(1);
        }

        scrollToCurrent();
    }

    private void start()
    {
        final MediaPlayerController service = mediaPlayerControllerLazy.getValue();
        final PlayerState state = service.getPlayerState();

        if (state == PAUSED || state == COMPLETED || state == STOPPED)
        {
            service.start();
        }
        else if (state == IDLE)
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();

            final int current = mediaPlayerControllerLazy.getValue().getCurrentPlayingNumberOnPlaylist();

            if (current == -1)
            {
                service.play(0);
            }
            else
            {
                service.play(current);
            }
        }
    }

    private void onDownloadListChanged()
    {
        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
        if (mediaPlayerController == null)
        {
            return;
        }

        final List<DownloadFile> list = mediaPlayerController.getPlayList();

        emptyTextView.setText(R.string.download_empty);
        final SongListAdapter adapter = new SongListAdapter(getContext(), list);
        playlistView.setAdapter(adapter);

        playlistView.setDragSortListener(new DragSortListView.DragSortListener()
        {
            @Override
            public void drop(int from, int to)
            {
                if (from != to)
                {
                    DownloadFile item = adapter.getItem(from);
                    adapter.remove(item);
                    adapter.notifyDataSetChanged();
                    adapter.insert(item, to);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void drag(int from, int to)
            {

            }

            @Override
            public void remove(int which)
            {
                DownloadFile item = adapter.getItem(which);
                MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

                if (item == null || mediaPlayerController == null)
                {
                    return;
                }

                DownloadFile currentPlaying = mediaPlayerController.getCurrentPlaying();

                if (currentPlaying == item)
                {
                    mediaPlayerControllerLazy.getValue().next();
                }

                adapter.remove(item);
                adapter.notifyDataSetChanged();

                String songRemoved = String.format(getResources().getString(R.string.download_song_removed), item.getSong().getTitle());

                Util.toast(getContext(), songRemoved);

                onDownloadListChanged();
                onCurrentChanged();
            }
        });

        emptyTextView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        currentRevision = mediaPlayerController.getPlayListUpdateRevision();

        switch (mediaPlayerController.getRepeatMode())
        {
            case OFF:
                repeatButton.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.media_repeat_off));
                break;
            case ALL:
                repeatButton.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.media_repeat_all));
                break;
            case SINGLE:
                repeatButton.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.media_repeat_single));
                break;
            default:
                break;
        }
    }

    private void onCurrentChanged()
    {
        MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

        if (mediaPlayerController == null)
        {
            return;
        }

        currentPlaying = mediaPlayerController.getCurrentPlaying();

        scrollToCurrent();

        long totalDuration = mediaPlayerController.getPlayListDuration();
        long totalSongs = mediaPlayerController.getPlaylistSize();
        int currentSongIndex = mediaPlayerController.getCurrentPlayingNumberOnPlaylist() + 1;

        String duration = Util.formatTotalDuration(totalDuration);

        String trackFormat = String.format(Locale.getDefault(), "%d / %d", currentSongIndex, totalSongs);

        if (currentPlaying != null)
        {
            currentSong = currentPlaying.getSong();
            songTitleTextView.setText(currentSong.getTitle());
            albumTextView.setText(currentSong.getAlbum());
            artistTextView.setText(currentSong.getArtist());
            downloadTrackTextView.setText(trackFormat);
            downloadTotalDurationTextView.setText(duration);
            imageLoaderProvider.getValue().getImageLoader().loadImage(albumArtImageView, currentSong, true, 0);

            displaySongRating();
        }
        else
        {
            currentSong = null;
            songTitleTextView.setText(null);
            albumTextView.setText(null);
            artistTextView.setText(null);
            downloadTrackTextView.setText(null);
            downloadTotalDurationTextView.setText(null);
            imageLoaderProvider.getValue().getImageLoader().loadImage(albumArtImageView, null, true, 0);
        }
    }

    private void onSliderProgressChanged()
    {
        MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

        if (mediaPlayerController == null || onProgressChangedTask != null)
        {
            return;
        }

        onProgressChangedTask = new SilentBackgroundTask<Void>(getActivity())
        {
            MediaPlayerController mediaPlayerController;
            boolean isJukeboxEnabled;
            int millisPlayed;
            Integer duration;
            PlayerState playerState;

            @Override
            protected Void doInBackground()
            {
                this.mediaPlayerController = mediaPlayerControllerLazy.getValue();
                isJukeboxEnabled = this.mediaPlayerController.isJukeboxEnabled();
                millisPlayed = Math.max(0, this.mediaPlayerController.getPlayerPosition());
                duration = this.mediaPlayerController.getPlayerDuration();
                playerState = mediaPlayerControllerLazy.getValue().getPlayerState();
                return null;
            }

            @Override
            protected void done(final Void result)
            {
                if (cancellationToken.isCancellationRequested()) return;
                if (currentPlaying != null)
                {
                    final int millisTotal = duration == null ? 0 : duration;

                    positionTextView.setText(Util.formatTotalDuration(millisPlayed, true));
                    durationTextView.setText(Util.formatTotalDuration(millisTotal, true));
                    progressBar.setMax(millisTotal == 0 ? 100 : millisTotal); // Work-around for apparent bug.
                    progressBar.setProgress(millisPlayed);
                    progressBar.setEnabled(currentPlaying.isWorkDone() || isJukeboxEnabled);
                }
                else
                {
                    positionTextView.setText(R.string.util_zero_time);
                    durationTextView.setText(R.string.util_no_time);
                    progressBar.setProgress(0);
                    progressBar.setMax(0);
                    progressBar.setEnabled(false);
                }

                switch (playerState)
                {
                    case DOWNLOADING:
                        int progress = currentPlaying != null ? currentPlaying.getProgress().getValue() : 0;
                        String downloadStatus = getResources().getString(R.string.download_playerstate_downloading, Util.formatPercentage(progress));
                        FragmentTitle.Companion.setTitle(PlayerFragment.this, downloadStatus);
                        break;
                    case PREPARING:
                        FragmentTitle.Companion.setTitle(PlayerFragment.this, R.string.download_playerstate_buffering);
                        break;
                    case STARTED:
                        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

                        if (mediaPlayerController != null && mediaPlayerController.isShufflePlayEnabled())
                        {
                            FragmentTitle.Companion.setTitle(PlayerFragment.this, R.string.download_playerstate_playing_shuffle);
                        }
                        else
                        {
                            FragmentTitle.Companion.setTitle(PlayerFragment.this, R.string.common_appname);
                        }
                        break;
                    default:
                        FragmentTitle.Companion.setTitle(PlayerFragment.this, R.string.common_appname);
                        break;
                    case IDLE:
                    case PREPARED:
                    case STOPPED:
                    case PAUSED:
                    case COMPLETED:
                        break;
                }

                switch (playerState)
                {
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

                // TODO: It would be a lot nicer if MediaPlayerController would send an event when this is necessary instead of updating every time
                displaySongRating();

                onProgressChangedTask = null;
            }
        };
        onProgressChangedTask.execute();
    }

    private void changeProgress(final int ms)
    {
        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
        if (mediaPlayerController == null)
        {
            return;
        }

        new SilentBackgroundTask<Void>(getActivity())
        {
            int msPlayed;
            Integer duration;
            int seekTo;

            @Override
            protected Void doInBackground()
            {
                msPlayed = Math.max(0, mediaPlayerController.getPlayerPosition());
                duration = mediaPlayerController.getPlayerDuration();

                final int msTotal = duration;
                seekTo = Math.min(msPlayed + ms, msTotal);
                mediaPlayerController.seekTo(seekTo);
                return null;
            }

            @Override
            protected void done(final Void result)
            {
                progressBar.setProgress(seekTo);
            }
        }.execute();
    }

    @Override
    public boolean onDown(final MotionEvent me)
    {
        return false;
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY)
    {

        final MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();

        if (mediaPlayerController == null || e1 == null || e2 == null)
        {
            return false;
        }

        float e1X = e1.getX();
        float e2X = e2.getX();
        float e1Y = e1.getY();
        float e2Y = e2.getY();
        float absX = Math.abs(velocityX);
        float absY = Math.abs(velocityY);

        // Right to Left swipe
        if (e1X - e2X > swipeDistance && absX > swipeVelocity)
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
            mediaPlayerController.next();
            onCurrentChanged();
            onSliderProgressChanged();
            return true;
        }

        // Left to Right swipe
        if (e2X - e1X > swipeDistance && absX > swipeVelocity)
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
            mediaPlayerController.previous();
            onCurrentChanged();
            onSliderProgressChanged();
            return true;
        }

        // Top to Bottom swipe
        if (e2Y - e1Y > swipeDistance && absY > swipeVelocity)
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
            mediaPlayerController.seekTo(mediaPlayerController.getPlayerPosition() + 30000);
            onSliderProgressChanged();
            return true;
        }

        // Bottom to Top swipe
        if (e1Y - e2Y > swipeDistance && absY > swipeVelocity)
        {
            networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
            mediaPlayerController.seekTo(mediaPlayerController.getPlayerPosition() - 8000);
            onSliderProgressChanged();
            return true;
        }

        return false;
    }

    @Override
    public void onLongPress(final MotionEvent e)
    {
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY)
    {
        return false;
    }

    @Override
    public void onShowPress(final MotionEvent e)
    {
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e)
    {
        return false;
    }

    public static SeekBar getProgressBar()
    {
        return progressBar;
    }

    private void displaySongRating()
    {
        int rating = currentSong == null || currentSong.getUserRating() == null ? 0 : currentSong.getUserRating();
        fiveStar1ImageView.setImageDrawable(rating > 0 ? fullStar : hollowStar);
        fiveStar2ImageView.setImageDrawable(rating > 1 ? fullStar : hollowStar);
        fiveStar3ImageView.setImageDrawable(rating > 2 ? fullStar : hollowStar);
        fiveStar4ImageView.setImageDrawable(rating > 3 ? fullStar : hollowStar);
        fiveStar5ImageView.setImageDrawable(rating > 4 ? fullStar : hollowStar);
    }

    private void setSongRating(final int rating)
    {
        if (currentSong == null)
            return;

        displaySongRating();
        mediaPlayerControllerLazy.getValue().setSongRating(rating);
    }

    private void showSavePlaylistDialog() {
        final AlertDialog.Builder builder;

        final LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final View layout = layoutInflater.inflate(R.layout.save_playlist, (ViewGroup) getActivity().findViewById(R.id.save_playlist_root));

        if (layout != null)
        {
            playlistNameView = layout.findViewById(R.id.save_playlist_name);
        }

        builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.download_playlist_title);
        builder.setMessage(R.string.download_playlist_name);
        builder.setPositiveButton(R.string.common_save, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(final DialogInterface dialog, final int clickId)
            {
                savePlaylistInBackground(String.valueOf(playlistNameView.getText()));
            }
        });
        builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(final DialogInterface dialog, final int clickId)
            {
                dialog.cancel();
            }
        });
        builder.setView(layout);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();

        final String playlistName = mediaPlayerControllerLazy.getValue().getSuggestedPlaylistName();
        if (playlistName != null)
        {
            playlistNameView.setText(playlistName);
        }
        else
        {
            final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            playlistNameView.setText(dateFormat.format(new Date()));
        }

        dialog.show();
    }
}
