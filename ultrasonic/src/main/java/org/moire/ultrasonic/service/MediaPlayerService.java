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
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.koin.java.standalone.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.DownloadActivity;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x1;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x2;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x3;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x4;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.SimpleServiceBinder;
import org.moire.ultrasonic.util.Util;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;
import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARING;
import static org.moire.ultrasonic.domain.PlayerState.STARTED;
import static org.moire.ultrasonic.domain.PlayerState.STOPPED;

public class MediaPlayerService extends Service
{
    private static final String TAG = MediaPlayerService.class.getSimpleName();
    private static final String NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic";
    private static final String NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service";
    private static final int NOTIFICATION_ID = 3033;

    private static MediaPlayerService instance = null;

    private final IBinder binder = new SimpleServiceBinder<>(this);
    private final Scrobbler scrobbler = new Scrobbler();

    public Lazy<JukeboxService> jukeboxService = inject(JukeboxService.class);
    private Lazy<DownloadQueueSerializer> downloadQueueSerializer = inject(DownloadQueueSerializer.class);
    private Lazy<ShufflePlayBuffer> shufflePlayBufferLazy = inject(ShufflePlayBuffer.class);
    private Lazy<Downloader> downloaderLazy = inject(Downloader.class);
    private Lazy<Player> playerLazy = inject(Player.class);
    private Player player;
    private Downloader downloader;
    private ShufflePlayBuffer shufflePlayBuffer;

    private boolean isInForeground = false;
    private NotificationCompat.Builder notificationBuilder;

    public RepeatMode getRepeatMode() { return Util.getRepeatMode(this); }

    public static MediaPlayerService getInstance(Context context)
    {
        for (int i = 0; i < 5; i++)
        {
            if (instance != null) return instance;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                context.startForegroundService(new Intent(context, MediaPlayerService.class));
            }
            else
            {
                context.startService(new Intent(context, MediaPlayerService.class));
            }

            Util.sleepQuietly(50L);
        }

		return instance;
    }

    public static MediaPlayerService getRunningInstance()
    {
        return instance;
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
        player = playerLazy.getValue();
        shufflePlayBuffer = shufflePlayBufferLazy.getValue();

        downloader.onCreate();
        shufflePlayBuffer.onCreate();

        player.onCreate();
        setupOnCurrentPlayingChangedHandler();
        setupOnPlayerStateChangedHandler();
        setupOnSongCompletedHandler();
        player.onPrepared = new Runnable() {
            @Override
            public void run() {
                downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList,
                        downloader.getCurrentPlayingIndex(), getPlayerPosition());
            }
        };
        player.onNextSongRequested = new Runnable() {
            @Override
            public void run() {
                setNextPlaying();
            }
        };

        // Create Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setLightColor(android.R.color.holo_blue_dark);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        // We should use a single notification builder, otherwise the notification may not be updated
        notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        // Update notification early. It is better to show an empty one temporarily than waiting too long and letting Android kill the app
        updateNotification(IDLE, null);
        instance = this;

        Log.i(TAG, "MediaPlayerService created");
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

        try
        {
            player.onDestroy();
            shufflePlayBuffer.onDestroy();
            downloader.onDestroy();
        }
        catch (Throwable ignored)
        {
        }

        Log.i(TAG, "MediaPlayerService stopped");
    }

    public synchronized void seekTo(int position)
    {
        if (jukeboxService.getValue().isEnabled())
        {
            jukeboxService.getValue().skip(downloader.getCurrentPlayingIndex(), position / 1000);
        }
        else
        {
            player.seekTo(position);
        }
    }

    public synchronized int getPlayerPosition()
    {
        if (player.playerState == IDLE || player.playerState == DOWNLOADING || player.playerState == PREPARING)
        {
            return 0;
        }

        return jukeboxService.getValue().isEnabled() ? jukeboxService.getValue().getPositionSeconds() * 1000 :
                player.getPlayerPosition();
    }

    public synchronized int getPlayerDuration()
    {
        return player.getPlayerDuration();
    }

    public synchronized void setCurrentPlaying(int currentPlayingIndex)
    {
        try
        {
            player.setCurrentPlaying(downloader.downloadList.get(currentPlayingIndex));
        }
        catch (IndexOutOfBoundsException x)
        {
            // Ignored
        }
    }

    public void setupOnCurrentPlayingChangedHandler()
    {
        player.onCurrentPlayingChanged = new Consumer<DownloadFile>() {
            @Override
            public void accept(DownloadFile currentPlaying) {
                if (currentPlaying != null)
                {
                    Util.broadcastNewTrackInfo(MediaPlayerService.this, currentPlaying.getSong());
                    Util.broadcastA2dpMetaDataChange(MediaPlayerService.this, getPlayerPosition(), currentPlaying,
                            downloader.getDownloads().size(), downloader.getCurrentPlayingIndex() + 1);
                }
                else
                {
                    Util.broadcastNewTrackInfo(MediaPlayerService.this, null);
                    Util.broadcastA2dpMetaDataChange(MediaPlayerService.this, getPlayerPosition(), null,
                            downloader.getDownloads().size(), downloader.getCurrentPlayingIndex() + 1);
                }

                // Update widget
                PlayerState playerState = player.playerState;
                MusicDirectory.Entry song = currentPlaying == null? null : currentPlaying.getSong();
                UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
                UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, true);
                UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
                UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);

                SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

                if (currentPlaying != null)
                {
                    if (tabInstance != null) {
                        updateNotification(player.playerState, currentPlaying);
                        tabInstance.showNowPlaying();
                    }
                }
                else
                {
                    if (tabInstance != null)
                    {
                        tabInstance.hideNowPlaying();
                        stopForeground(true);
                        player.clearRemoteControl();
                        isInForeground = false;
                        stopSelf();
                    }
                }
            }
        };
    }

    public synchronized void setNextPlaying()
    {
        boolean gaplessPlayback = Util.getGaplessPlaybackPreference(this);

        if (!gaplessPlayback)
        {
            player.setNextPlaying(null);
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
                    break;
                default:
                    break;
            }
        }

        player.clearNextPlaying();

        if (index < downloader.downloadList.size() && index != -1)
        {
            player.setNextPlaying(downloader.downloadList.get(index));
        }
        else
        {
            player.setNextPlaying(null);
        }
    }

    public synchronized void togglePlayPause()
    {
        if (player.playerState == PAUSED || player.playerState == COMPLETED || player.playerState == STOPPED)
        {
            start();
        }
        else if (player.playerState == IDLE)
        {
            play();
        }
        else if (player.playerState == STARTED)
        {
            pause();
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
        Log.v(TAG, String.format("play requested for %d", index));
        if (index < 0 || index >= downloader.downloadList.size())
        {
            resetPlayback();
        }
        else
        {
            if (start)
            {
                if (jukeboxService.getValue().isEnabled())
                {
                    jukeboxService.getValue().skip(index, 0);
                    player.setPlayerState(STARTED);
                }
                else
                {
                    player.play(downloader.downloadList.get(index));
                }
            }

            downloader.checkDownloads();
            setNextPlaying();
        }
    }

    private synchronized void resetPlayback()
    {
        player.reset();
        player.setCurrentPlaying(null);
        downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList,
                downloader.getCurrentPlayingIndex(), getPlayerPosition());
    }

    public synchronized void pause()
    {
        if (player.playerState == STARTED)
        {
            if (jukeboxService.getValue().isEnabled())
            {
                jukeboxService.getValue().stop();
            }
            else
            {
                player.pause();
            }
            player.setPlayerState(PAUSED);
        }
    }

    public synchronized void stop()
    {
        if (player.playerState == STARTED)
        {
            if (jukeboxService.getValue().isEnabled())
            {
                jukeboxService.getValue().stop();
            }
            else
            {
                player.pause();
            }
        }
        player.setPlayerState(STOPPED);
    }

    public synchronized void start()
    {
        if (jukeboxService.getValue().isEnabled())
        {
            jukeboxService.getValue().start();
        }
        else
        {
            player.start();
        }
        player.setPlayerState(STARTED);
    }

    public void setupOnPlayerStateChangedHandler()
    {
        player.onPlayerStateChanged = new BiConsumer<PlayerState, DownloadFile>() {
            @Override
            public void accept(PlayerState playerState, DownloadFile currentPlaying) {
                if (playerState == PAUSED)
                {
                    downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
                }

                boolean showWhenPaused = (playerState != PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(MediaPlayerService.this));
                boolean show = playerState == PlayerState.STARTED || showWhenPaused;

                Util.broadcastPlaybackStatusChange(MediaPlayerService.this, playerState);
                Util.broadcastA2dpPlayStatusChange(MediaPlayerService.this, playerState, currentPlaying.getSong(),
                        downloader.downloadList.size() + downloader.backgroundDownloadList.size(),
                        downloader.downloadList.indexOf(currentPlaying) + 1, getPlayerPosition());

                MusicDirectory.Entry song = currentPlaying.getSong();
                // Update widget
                UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
                UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, true);
                UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
                UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(MediaPlayerService.this, song, playerState == PlayerState.STARTED, false);
                SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

                if (show)
                {
                    if (tabInstance != null)
                    {
                        // Only update notification is player state is one that will change the icon
                        if (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED)
                        {
                            updateNotification(playerState, currentPlaying);
                            tabInstance.showNowPlaying();
                        }
                    }
                }
                else
                {
                    if (tabInstance != null)
                    {
                        stopForeground(true);
                        player.clearRemoteControl();
                        isInForeground = false;
                        tabInstance.hideNowPlaying();
                        stopSelf();
                    }
                }

                if (playerState == STARTED)
                {
                    scrobbler.scrobble(MediaPlayerService.this, currentPlaying, false);
                }
                else if (playerState == COMPLETED)
                {
                    scrobbler.scrobble(MediaPlayerService.this, currentPlaying, true);
                }
            }
        };
    }

    private void setupOnSongCompletedHandler()
    {
        player.onSongCompleted = new Consumer<DownloadFile>() {
            @Override
            public void accept(DownloadFile currentPlaying) {
                int index = downloader.getCurrentPlayingIndex();

                if (currentPlaying != null)
                {
                    final MusicDirectory.Entry song = currentPlaying.getSong();

                    if (song != null && song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(MediaPlayerService.this))
                    {
                        MusicService musicService = MusicServiceFactory.getMusicService(MediaPlayerService.this);
                        try
                        {
                            musicService.deleteBookmark(song.getId(), MediaPlayerService.this, null);
                        }
                        catch (Exception ignored)
                        {

                        }
                    }
                }

                if (index != -1)
                {
                    switch (getRepeatMode())
                    {
                        case OFF:
                            if (index + 1 < 0 || index + 1 >= downloader.downloadList.size())
                            {
                                if (Util.getShouldClearPlaylist(MediaPlayerService.this))
                                {
                                    clear(true);
                                    jukeboxService.getValue().updatePlaylist();
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
            }
        };
    }

    public synchronized void clear(boolean serialize)
    {
        player.reset();
        downloader.clear();
        player.setCurrentPlaying(null);

        setNextPlaying();

        if (serialize) {
            downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList,
                    downloader.getCurrentPlayingIndex(), getPlayerPosition());
        }
    }

    public void updateNotification(PlayerState playerState, DownloadFile currentPlaying)
    {
        if (Util.isNotificationEnabled(this)) {
            if (isInForeground == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                }
                else {
                    final NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(this);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                }
                Log.w(TAG, "--- Updated notification");
            }
            else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification(playerState, currentPlaying));
                isInForeground = true;
                Log.w(TAG, "--- Created Foreground notification");
            }
        }
    }

    @SuppressWarnings("IconColors")
    private Notification buildForegroundNotification(PlayerState playerState, DownloadFile currentPlaying) {
        notificationBuilder.setSmallIcon(R.drawable.ic_stat_ultrasonic);

        notificationBuilder.setAutoCancel(false);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setOnlyAlertOnce(true);
        notificationBuilder.setWhen(System.currentTimeMillis());
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);

        RemoteViews contentView = new RemoteViews(this.getPackageName(), R.layout.notification);
        Util.linkButtons(this, contentView, false);
        RemoteViews bigView = new RemoteViews(this.getPackageName(), R.layout.notification_large);
        Util.linkButtons(this, bigView, false);

        notificationBuilder.setContent(contentView);

        Intent notificationIntent = new Intent(this, DownloadActivity.class);
        notificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));

        if (playerState == PlayerState.PAUSED || playerState == PlayerState.IDLE) {
            contentView.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark);
            bigView.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark);
        } else if (playerState == PlayerState.STARTED) {
            contentView.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark);
            bigView.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark);
        }

        if (currentPlaying != null) {
            final MusicDirectory.Entry song = currentPlaying.getSong();
            final String title = song.getTitle();
            final String text = song.getArtist();
            final String album = song.getAlbum();
            final int rating = song.getUserRating() == null ? 0 : song.getUserRating();
            final int imageSize = Util.getNotificationImageSize(this);

            try {
                final Bitmap nowPlayingImage = FileUtil.getAlbumArtBitmap(this, currentPlaying.getSong(), imageSize, true);
                if (nowPlayingImage == null) {
                    contentView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
                    bigView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
                } else {
                    contentView.setImageViewBitmap(R.id.notification_image, nowPlayingImage);
                    bigView.setImageViewBitmap(R.id.notification_image, nowPlayingImage);
                }
            } catch (Exception x) {
                Log.w(TAG, "Failed to get notification cover art", x);
                contentView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
                bigView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
            }

            contentView.setTextViewText(R.id.trackname, title);
            bigView.setTextViewText(R.id.trackname, title);
            contentView.setTextViewText(R.id.artist, text);
            bigView.setTextViewText(R.id.artist, text);
            contentView.setTextViewText(R.id.album, album);
            bigView.setTextViewText(R.id.album, album);

            boolean useFiveStarRating = KoinJavaComponent.get(FeatureStorage.class).isFeatureEnabled(Feature.FIVE_STAR_RATING);
            if (!useFiveStarRating)
                bigView.setViewVisibility(R.id.notification_rating, View.INVISIBLE);
            else {
                bigView.setImageViewResource(R.id.notification_five_star_1, rating > 0 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
                bigView.setImageViewResource(R.id.notification_five_star_2, rating > 1 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
                bigView.setImageViewResource(R.id.notification_five_star_3, rating > 2 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
                bigView.setImageViewResource(R.id.notification_five_star_4, rating > 3 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
                bigView.setImageViewResource(R.id.notification_five_star_5, rating > 4 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
            }
        }

        Notification notification = notificationBuilder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification.bigContentView = bigView;
        }

        return notification;
    }
}
