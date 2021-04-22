package org.moire.ultrasonic.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.NavigationActivity;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X1;
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X2;
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X3;
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider4X4;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.NowPlayingEventDistributor;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.SimpleServiceBinder;
import org.moire.ultrasonic.util.Util;

import java.util.ArrayList;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;
import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARING;
import static org.moire.ultrasonic.domain.PlayerState.STARTED;
import static org.moire.ultrasonic.domain.PlayerState.STOPPED;

/**
 * Android Foreground Service for playing music
 * while the rest of the Ultrasonic App is in the background.
 */
public class MediaPlayerService extends Service
{
    private static final String NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic";
    private static final String NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service";
    private static final int NOTIFICATION_ID = 3033;

    private static MediaPlayerService instance = null;
    private static final Object instanceLock = new Object();

    private final IBinder binder = new SimpleServiceBinder<>(this);
    private final Scrobbler scrobbler = new Scrobbler();

    public Lazy<JukeboxMediaPlayer> jukeboxMediaPlayer = inject(JukeboxMediaPlayer.class);
    private final Lazy<DownloadQueueSerializer> downloadQueueSerializerLazy = inject(DownloadQueueSerializer.class);
    private final Lazy<ShufflePlayBuffer> shufflePlayBufferLazy = inject(ShufflePlayBuffer.class);
    private final Lazy<Downloader> downloaderLazy = inject(Downloader.class);
    private final Lazy<LocalMediaPlayer> localMediaPlayerLazy = inject(LocalMediaPlayer.class);
    private final Lazy<NowPlayingEventDistributor> nowPlayingEventDistributor = inject(NowPlayingEventDistributor.class);
    private final Lazy<MediaPlayerLifecycleSupport> mediaPlayerLifecycleSupport = inject(MediaPlayerLifecycleSupport.class);

    private LocalMediaPlayer localMediaPlayer;
    private Downloader downloader;
    private ShufflePlayBuffer shufflePlayBuffer;
    private DownloadQueueSerializer downloadQueueSerializer;

    private MediaSessionCompat mediaSession;
    private MediaSessionCompat.Token mediaSessionToken;

    private boolean isInForeground = false;
    private NotificationCompat.Builder notificationBuilder;

    public RepeatMode getRepeatMode() { return Util.getRepeatMode(this); }

    public static MediaPlayerService getInstance(Context context)
    {
        synchronized (instanceLock) {
            for (int i = 0; i < 20; i++) {
                if (instance != null) return instance;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(new Intent(context, MediaPlayerService.class));
                } else {
                    context.startService(new Intent(context, MediaPlayerService.class));
                }

                Util.sleepQuietly(50L);
            }

            return instance;
        }
    }

    public static MediaPlayerService getRunningInstance()
    {
        synchronized (instanceLock)
        {
            return instance;
        }
    }

    public static void executeOnStartedMediaPlayerService(final Context context, final Consumer<MediaPlayerService> taskToExecute)
    {
        Thread t = new Thread()
        {
            public void run()
            {
                MediaPlayerService instance = getInstance(context);
                if (instance == null)
                {
                    Timber.e("ExecuteOnStartedMediaPlayerService failed to get a MediaPlayerService instance!");
                    return;
                }

                taskToExecute.accept(instance);
            }
        };
        t.start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        downloader = downloaderLazy.getValue();
        localMediaPlayer = localMediaPlayerLazy.getValue();
        shufflePlayBuffer = shufflePlayBufferLazy.getValue();
        downloadQueueSerializer = downloadQueueSerializerLazy.getValue();

        initMediaSessions();


        downloader.onCreate();
        shufflePlayBuffer.onCreate();

        localMediaPlayer.init();
        setupOnCurrentPlayingChangedHandler();
        setupOnPlayerStateChangedHandler();
        setupOnSongCompletedHandler();

        localMediaPlayer.onPrepared = () -> {
            downloadQueueSerializer.serializeDownloadQueue(
                    downloader.downloadList,
                    downloader.getCurrentPlayingIndex(),
                    getPlayerPosition()
            );
            return null;
        };

        localMediaPlayer.onNextSongRequested = this::setNextPlaying;

        // Create Notification Channel
        createNotificationChannel();

        // Update notification early. It is better to show an empty one temporarily than waiting too long and letting Android kill the app
        updateNotification(IDLE, null);

        instance = this;

        Timber.i("MediaPlayerService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        instance = null;

        try {
            localMediaPlayer.release();
            downloader.stop();
            shufflePlayBuffer.onDestroy();
            mediaSession.release();
        } catch (Throwable ignored) {
        }

        Timber.i("MediaPlayerService stopped");
    }

    private void stopIfIdle()
    {
        synchronized (instanceLock)
        {
            // currentPlaying could be changed from another thread in the meantime, so check again before stopping for good
            if (localMediaPlayer.currentPlaying == null || localMediaPlayer.playerState == STOPPED) stopSelf();
        }
    }

    public synchronized void seekTo(int position)
    {
        if (jukeboxMediaPlayer.getValue().isEnabled())
        {
            jukeboxMediaPlayer.getValue().skip(downloader.getCurrentPlayingIndex(), position / 1000);
        }
        else
        {
            localMediaPlayer.seekTo(position);
        }
    }

    public synchronized int getPlayerPosition()
    {
        if (localMediaPlayer.playerState == IDLE || localMediaPlayer.playerState == DOWNLOADING || localMediaPlayer.playerState == PREPARING)
        {
            return 0;
        }

        return jukeboxMediaPlayer.getValue().isEnabled() ? jukeboxMediaPlayer.getValue().getPositionSeconds() * 1000 :
                localMediaPlayer.getPlayerPosition();
    }

    public synchronized int getPlayerDuration()
    {
        return localMediaPlayer.getPlayerDuration();
    }

    public synchronized void setCurrentPlaying(int currentPlayingIndex)
    {
        try
        {
            localMediaPlayer.setCurrentPlaying(downloader.downloadList.get(currentPlayingIndex));
        }
        catch (IndexOutOfBoundsException x)
        {
            // Ignored
        }
    }

    public void setupOnCurrentPlayingChangedHandler() {
        localMediaPlayer.onCurrentPlayingChanged = (DownloadFile currentPlaying) -> {

            if (currentPlaying != null) {
                Util.broadcastNewTrackInfo(MediaPlayerService.this, currentPlaying.getSong());
                Util.broadcastA2dpMetaDataChange(MediaPlayerService.this, getPlayerPosition(), currentPlaying,
                        downloader.getDownloads().size(), downloader.getCurrentPlayingIndex() + 1);
            } else {
                Util.broadcastNewTrackInfo(MediaPlayerService.this, null);
                Util.broadcastA2dpMetaDataChange(MediaPlayerService.this, getPlayerPosition(), null,
                        downloader.getDownloads().size(), downloader.getCurrentPlayingIndex() + 1);
            }

            // Update widget
            PlayerState playerState = localMediaPlayer.playerState;
            MusicDirectory.Entry song = currentPlaying == null ? null : currentPlaying.getSong();
            UpdateWidget(playerState, song);

            if (currentPlaying != null) {
                updateNotification(localMediaPlayer.playerState, currentPlaying);
                nowPlayingEventDistributor.getValue().raiseShowNowPlayingEvent();
            } else {
                nowPlayingEventDistributor.getValue().raiseHideNowPlayingEvent();
                stopForeground(true);
                isInForeground = false;
                stopIfIdle();
            }

            return null;
        };
    }

    public synchronized void setNextPlaying()
    {
        boolean gaplessPlayback = Util.getGaplessPlaybackPreference(this);

        if (!gaplessPlayback)
        {
            localMediaPlayer.clearNextPlaying(true);
            return;
        }

        int index = downloader.getCurrentPlayingIndex();

        if (index != -1)
        {
            switch (getRepeatMode())
            {
                case OFF:
                    index += 1;
                    break;
                case ALL:
                    index = (index + 1) % downloader.downloadList.size();
                    break;
                case SINGLE:
                default:
                    break;
            }
        }

        localMediaPlayer.clearNextPlaying(false);

        if (index < downloader.downloadList.size() && index != -1)
        {
            localMediaPlayer.setNextPlaying(downloader.downloadList.get(index));
        }
        else
        {
            localMediaPlayer.clearNextPlaying(true);
        }
    }

    public synchronized void togglePlayPause()
    {
        if (localMediaPlayer.playerState == PAUSED || localMediaPlayer.playerState == COMPLETED || localMediaPlayer.playerState == STOPPED)
        {
            start();
        }
        else if (localMediaPlayer.playerState == IDLE)
        {
            play();
        }
        else if (localMediaPlayer.playerState == STARTED)
        {
            pause();
        }
    }

    public synchronized void resumeOrPlay()
    {
        if (localMediaPlayer.playerState == PAUSED || localMediaPlayer.playerState == COMPLETED || localMediaPlayer.playerState == STOPPED)
        {
            start();
        }
        else if (localMediaPlayer.playerState == IDLE)
        {
            play();
        }
    }

    /**
     * Plays either the current song (resume) or the first/next one in queue.
     */
    public synchronized void play()
    {
        int current = downloader.getCurrentPlayingIndex();
        if (current == -1)
        {
            play(0);
        }
        else
        {
            play(current);
        }
    }

    public synchronized void play(int index)
    {
        play(index, true);
    }

    public synchronized void play(int index, boolean start)
    {
        Timber.v("play requested for %d", index);
        if (index < 0 || index >= downloader.downloadList.size())
        {
            resetPlayback();
        }
        else
        {
            setCurrentPlaying(index);

            if (start)
            {
                if (jukeboxMediaPlayer.getValue().isEnabled())
                {
                    jukeboxMediaPlayer.getValue().skip(index, 0);
                    localMediaPlayer.setPlayerState(STARTED);
                }
                else
                {
                    localMediaPlayer.play(downloader.downloadList.get(index));
                }
            }

            downloader.checkDownloads();
            setNextPlaying();
        }
    }

    private synchronized void resetPlayback()
    {
        localMediaPlayer.reset();
        localMediaPlayer.setCurrentPlaying(null);
        downloadQueueSerializer.serializeDownloadQueue(downloader.downloadList,
                downloader.getCurrentPlayingIndex(), getPlayerPosition());
    }

    public synchronized void pause()
    {
        if (localMediaPlayer.playerState == STARTED)
        {
            if (jukeboxMediaPlayer.getValue().isEnabled())
            {
                jukeboxMediaPlayer.getValue().stop();
            }
            else
            {
                localMediaPlayer.pause();
            }
            localMediaPlayer.setPlayerState(PAUSED);
        }
    }

    public synchronized void stop()
    {
        if (localMediaPlayer.playerState == STARTED)
        {
            if (jukeboxMediaPlayer.getValue().isEnabled())
            {
                jukeboxMediaPlayer.getValue().stop();
            }
            else
            {
                localMediaPlayer.pause();
            }
        }
        localMediaPlayer.setPlayerState(STOPPED);
    }

    public synchronized void start()
    {
        if (jukeboxMediaPlayer.getValue().isEnabled())
        {
            jukeboxMediaPlayer.getValue().start();
        }
        else
        {
            localMediaPlayer.start();
        }
        localMediaPlayer.setPlayerState(STARTED);
    }

    private void UpdateWidget(PlayerState playerState, MusicDirectory.Entry song) {
        UltrasonicAppWidgetProvider4X1.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
        UltrasonicAppWidgetProvider4X2.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, true);
        UltrasonicAppWidgetProvider4X3.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
        UltrasonicAppWidgetProvider4X4.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
    }

    public void setupOnPlayerStateChangedHandler() {
        localMediaPlayer.onPlayerStateChanged = (PlayerState playerState, DownloadFile currentPlaying) -> {
            // Notify MediaSession
            updateMediaSession(currentPlaying, playerState);

            if (playerState == PAUSED) {
                downloadQueueSerializer.serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
            }

            boolean showWhenPaused = (playerState != PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(MediaPlayerService.this));
            boolean show = playerState == PlayerState.STARTED || showWhenPaused;
            MusicDirectory.Entry song = currentPlaying == null ? null : currentPlaying.getSong();

            Util.broadcastPlaybackStatusChange(MediaPlayerService.this, playerState);
            Util.broadcastA2dpPlayStatusChange(MediaPlayerService.this, playerState, song,
                    downloader.downloadList.size() + downloader.backgroundDownloadList.size(),
                    downloader.downloadList.indexOf(currentPlaying) + 1, getPlayerPosition());

            // Update widget
            UpdateWidget(playerState, song);

            if (show) {
                // Only update notification if player state is one that will change the icon
                if (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED) {
                    updateNotification(playerState, currentPlaying);
                    nowPlayingEventDistributor.getValue().raiseShowNowPlayingEvent();
                }
            } else {
                nowPlayingEventDistributor.getValue().raiseHideNowPlayingEvent();
                stopForeground(true);
                isInForeground = false;
                stopIfIdle();
            }

            if (playerState == STARTED) {
                scrobbler.scrobble(MediaPlayerService.this, currentPlaying, false);
            } else if (playerState == COMPLETED) {
                scrobbler.scrobble(MediaPlayerService.this, currentPlaying, true);
            }

            return null;
        };
    }

    private void setupOnSongCompletedHandler() {
        localMediaPlayer.onSongCompleted = (DownloadFile currentPlaying) -> {
            int index = downloader.getCurrentPlayingIndex();

            if (currentPlaying != null) {
                final MusicDirectory.Entry song = currentPlaying.getSong();

                if (song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(MediaPlayerService.this)) {
                    MusicService musicService = MusicServiceFactory.getMusicService(MediaPlayerService.this);
                    try {
                        musicService.deleteBookmark(song.getId(), MediaPlayerService.this);
                    } catch (Exception ignored) {

                    }
                }
            }

            if (index != -1) {
                switch (getRepeatMode()) {
                    case OFF:
                        if (index + 1 < 0 || index + 1 >= downloader.downloadList.size()) {
                            if (Util.getShouldClearPlaylist(MediaPlayerService.this)) {
                                clear(true);
                                jukeboxMediaPlayer.getValue().updatePlaylist();
                            }

                            resetPlayback();
                            break;
                        }

                        play(index + 1);
                        break;
                    case ALL:
                        play((index + 1) % downloader.downloadList.size());
                        break;
                    case SINGLE:
                        play(index);
                        break;
                    default:
                        break;
                }
            }

            return null;
        };
    }

    public synchronized void clear(boolean serialize)
    {
        localMediaPlayer.reset();
        downloader.clear();
        localMediaPlayer.setCurrentPlaying(null);

        setNextPlaying();

        if (serialize) {
            downloadQueueSerializer.serializeDownloadQueue(downloader.downloadList,
                    downloader.getCurrentPlayingIndex(), getPlayerPosition());
        }
    }

    private void updateMediaSession(DownloadFile currentPlaying, PlayerState playerState) {
        // Set Metadata
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder();
        Context context = getApplicationContext();

        if (currentPlaying != null) {
            try {
                MusicDirectory.Entry song = currentPlaying.getSong();

                Bitmap cover = FileUtil.getAlbumArtBitmap(context, song,
                        Util.getMinDisplayMetric(context), true
                );

                metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L);
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.getArtist());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getAlbum());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle());
                metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover);
            } catch (Exception e) {
                Timber.e(e, "Error setting the metadata");
            }
        }

        // Save the metadata
        mediaSession.setMetadata(metadata.build());

        // Create playback State
        PlaybackStateCompat.Builder playbackState = new PlaybackStateCompat.Builder();
        int state = (playerState == STARTED) ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        // If we set the playback position correctly, we can get a nice seek bar :)
        playbackState.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0F);

        // Set Active state
        mediaSession.setActive(playerState == STARTED);

        // Save the playback state
        mediaSession.setPlaybackState(playbackState.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setLightColor(android.R.color.holo_blue_dark);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    public void updateNotification(PlayerState playerState, DownloadFile currentPlaying)
    {
        if (Util.isNotificationEnabled(this)) {
            if (isInForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                } else {
                    final NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(this);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                }
                Timber.w("--- Updated notification");
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                isInForeground = true;
                Timber.w("--- Created Foreground notification");
            }
        }
    }


    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    private Notification buildForegroundNotification(PlayerState playerState, DownloadFile currentPlaying) {
        // Init
        Context context = getApplicationContext();
        MusicDirectory.Entry song = (currentPlaying != null) ? currentPlaying.getSong() : null;
        PendingIntent stopIntent = getPendingIntentForMediaAction(context, KeyEvent.KEYCODE_MEDIA_STOP, 100);

        // We should use a single notification builder, otherwise the notification may not be updated
        if (notificationBuilder == null) {
            notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

            // Set some values that never change
            notificationBuilder.setSmallIcon(R.drawable.ic_stat_ultrasonic);
            notificationBuilder.setAutoCancel(false);
            notificationBuilder.setOngoing(true);
            notificationBuilder.setOnlyAlertOnce(true);
            notificationBuilder.setWhen(System.currentTimeMillis());
            notificationBuilder.setShowWhen(false);
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);

            // Add content intent (when user taps on notification)
            notificationBuilder.setContentIntent(getPendingIntentForContent());

            // This intent is executed when the user closes the notification
            notificationBuilder.setDeleteIntent(stopIntent);
        }

        // Use the Media Style, to enable native Android support for playback notification
        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle();
        style.setMediaSession(mediaSessionToken);

        // Clear old actions
        notificationBuilder.clearActions();

        // Add actions
        int[] compactActions = addActions(context, notificationBuilder, playerState, song);

        // Configure shortcut actions
        style.setShowActionsInCompactView(compactActions);
        notificationBuilder.setStyle(style);

        // Set song title, artist and cover if possible
        if (song != null) {
            int iconSize = (int) (256 * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, song, iconSize, true);
            notificationBuilder.setContentTitle(song.getTitle());
            notificationBuilder.setContentText(song.getArtist());
            notificationBuilder.setLargeIcon(bitmap);
            notificationBuilder.setSubText(song.getAlbum());
        }

        return notificationBuilder.build();
    }


    private int[] addActions(Context context, NotificationCompat.Builder notificationBuilder, PlayerState playerState, MusicDirectory.Entry song) {
        ArrayList<Integer> compactActionList = new ArrayList<>();
        int numActions = 0; // we start and 0 and then increment by 1 for each call to generateAction


        // Star
        if (song != null) {
            notificationBuilder.addAction(generateStarUnstarAction(context, numActions, song.getStarred()));
        }
        numActions++;

        // Next
        notificationBuilder.addAction(generateAction(context, numActions));
        compactActionList.add(numActions);
        numActions++;

        // Play/Pause button
        notificationBuilder.addAction(generatePlayPauseAction(context, numActions, playerState));
        compactActionList.add(numActions);
        numActions++;

        // Previous
        notificationBuilder.addAction(generateAction(context, numActions));
        compactActionList.add(numActions);
        numActions++;

        // Close
        notificationBuilder.addAction(generateAction(context, numActions));

        int[] actionArray = new int[compactActionList.size()];

        for (int i = 0; i < actionArray.length; i++) {
            actionArray[i] = compactActionList.get(i);
        }

        return actionArray;
        //notificationBuilder.setShowActionsInCompactView())
    }


    private NotificationCompat.Action generateAction(Context context, int requestCode) {
        int keycode;
        int icon;
        String label;

        // If you change the order here, also update the requestCode in updatePlayPauseAction()!
        switch (requestCode) {
            case 1:
                keycode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                label = getString(R.string.common_play_previous);
                icon = R.drawable.media_backward_medium_dark;
                break;
            case 2:
                // Is handled in generatePlayPauseAction()
                return null;
            case 3:
                keycode = KeyEvent.KEYCODE_MEDIA_NEXT;
                label = getString(R.string.common_play_next);
                icon = R.drawable.media_forward_medium_dark;
                break;
            case 4:
                keycode = KeyEvent.KEYCODE_MEDIA_STOP;
                label = getString(R.string.buttons_stop);
                icon = R.drawable.ic_baseline_close_24;
                break;
            default:
                return null;
        }

        PendingIntent pendingIntent = getPendingIntentForMediaAction(context, keycode, requestCode);

        return new NotificationCompat.Action.Builder(icon, label, pendingIntent).build();
    }

    private NotificationCompat.Action generatePlayPauseAction(Context context, int requestCode, PlayerState playerState) {

        boolean isPlaying = (playerState == STARTED);
        PendingIntent pendingIntent = getPendingIntentForMediaAction(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, requestCode);

        String label;
        int icon;

        if (isPlaying) {
            label = getString(R.string.common_pause);
            icon = R.drawable.media_pause_large_dark;
        } else {
            label = getString(R.string.common_play);
            icon = R.drawable.media_start_large_dark;
        }

        return new NotificationCompat.Action.Builder(icon, label, pendingIntent).build();
    }


    private NotificationCompat.Action generateStarUnstarAction(Context context, int requestCode, Boolean isStarred) {

        int keyCode;
        String label;
        int icon;
        keyCode = KeyEvent.KEYCODE_STAR;

        if (isStarred) {
            label = getString(R.string.download_menu_star);
            icon = R.drawable.ic_star_full_dark;

        } else {
            label = getString(R.string.download_menu_star);
            icon = R.drawable.ic_star_hollow_dark;
        }

        PendingIntent pendingIntent = getPendingIntentForMediaAction(context, keyCode, requestCode);

        return new NotificationCompat.Action.Builder(icon, label, pendingIntent).build();
    }


    private PendingIntent getPendingIntentForContent() {
        Intent notificationIntent = new Intent(this, NavigationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_SHOW_PLAYER, true);
        return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPendingIntentForMediaAction(Context context, int keycode, int requestCode) {
        Intent intent = new Intent(Constants.CMD_PROCESS_KEYCODE);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keycode));

        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initMediaSessions() {

        mediaSession = new MediaSessionCompat(getApplicationContext(), "UltrasonicService");
        mediaSessionToken = mediaSession.getSessionToken();
        //mediaController = new MediaControllerCompat(getApplicationContext(), mediaSessionToken);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
                                     @Override
                                     public void onPlay() {
                                         super.onPlay();
                                         play();
                                         Timber.w("Media Session Callback: onPlay");
                                     }

                                     @Override
                                     public void onPause() {
                                         super.onPause();
                                         pause();
                                         Timber.w("Media Session Callback: onPause");
                                     }

                                     @Override
                                     public void onStop() {
                                         super.onStop();
                                         stop();
                                         Timber.w("Media Session Callback: onStop");
                                     }

                                     @Override
                                     public void onSeekTo(long pos) {
                                         super.onSeekTo(pos);
                                     }

                                     @Override
                                     public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                                         // This probably won't be necessary once we implement more
                                         // of the modern media APIs, like the MediaController etc.
                                         KeyEvent event = (KeyEvent) mediaButtonEvent.getExtras().get("android.intent.extra.KEY_EVENT");
                                         MediaPlayerLifecycleSupport lifecycleSupport = mediaPlayerLifecycleSupport.getValue();
                                         lifecycleSupport.handleKeyEvent(event);

                                         return true;
                                     }

                                 }
        );
    }
}
