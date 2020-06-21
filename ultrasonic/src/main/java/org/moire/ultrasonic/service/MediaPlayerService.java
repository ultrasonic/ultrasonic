package org.moire.ultrasonic.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RemoteControlClient;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.koin.java.standalone.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.DownloadActivity;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x1;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x2;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x3;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x4;
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.SimpleServiceBinder;
import org.moire.ultrasonic.util.StreamProxy;
import org.moire.ultrasonic.util.Util;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARED;
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

    public static boolean equalizerAvailable;
    public static boolean visualizerAvailable;
    public static final List<DownloadFile> downloadList = new ArrayList<DownloadFile>();
    public static final List<DownloadFile> backgroundDownloadList = new ArrayList<DownloadFile>();

    private final IBinder binder = new SimpleServiceBinder<>(this);
    private PowerManager.WakeLock wakeLock;

    private Looper mediaPlayerLooper;
    private MediaPlayer mediaPlayer;
    private MediaPlayer nextMediaPlayer;
    private Handler mediaPlayerHandler;
    private AudioManager audioManager;

    public RemoteControlClient remoteControlClient;
    private EqualizerController equalizerController;
    private VisualizerController visualizerController;
    public static ShufflePlayBuffer shufflePlayBuffer;
    private final Scrobbler scrobbler = new Scrobbler();
    private CancellableTask bufferTask;
    public static DownloadFile currentPlaying;
    public static DownloadFile currentDownloading;
    public static DownloadFile nextPlaying;

    public static boolean jukeboxEnabled;
    public static JukeboxService jukeboxService;
    public static DownloadServiceLifecycleSupport lifecycleSupport;

    public static int cachedPosition;
    private PositionCache positionCache;
    private StreamProxy proxy;

    private static boolean nextSetup;
    private static CancellableTask nextPlayingTask;
    public static PlayerState playerState = IDLE;
    public static PlayerState nextPlayerState = IDLE;
    private boolean isInForeground = false;
    private static final List<DownloadFile> cleanupCandidates = new ArrayList<DownloadFile>();
    public static boolean shufflePlay;
    public static long revision;
    private int secondaryProgress = -1;

    private NotificationCompat.Builder notificationBuilder;

    static
    {
        try
        {
            EqualizerController.checkAvailable();
            equalizerAvailable = true;
        }
        catch (Throwable t)
        {
            equalizerAvailable = false;
        }
    }

    static
    {
        try
        {
            VisualizerController.checkAvailable();
            visualizerAvailable = true;
        }
        catch (Throwable t)
        {
            visualizerAvailable = false;
        }
    }

    public static synchronized int size() { return downloadList.size(); }
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

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("MediaPlayerService");

                Looper.prepare();

                if (mediaPlayer != null)
                {
                    mediaPlayer.release();
                }

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setWakeMode(MediaPlayerService.this, PowerManager.PARTIAL_WAKE_LOCK);

                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
                {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int what, int more)
                    {
                        handleError(new Exception(String.format("MediaPlayer error: %d (%d)", what, more)));
                        return false;
                    }
                });

                try
                {
                    Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
                    i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                    sendBroadcast(i);
                }
                catch (Throwable e)
                {
                    // Froyo or lower
                }

                mediaPlayerLooper = Looper.myLooper();
                mediaPlayerHandler = new Handler(mediaPlayerLooper);
                Looper.loop();
            }
        }).start();

        audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        setUpRemoteControlClient();

        if (equalizerAvailable)
        {
            equalizerController = new EqualizerController(this, mediaPlayer);
            if (!equalizerController.isAvailable())
            {
                equalizerController = null;
            }
            else
            {
                equalizerController.loadSettings();
            }
        }

        if (visualizerAvailable)
        {
            visualizerController = new VisualizerController(mediaPlayer);
            if (!visualizerController.isAvailable())
            {
                visualizerController = null;
            }
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        wakeLock.setReferenceCounted(false);

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

        instance = this;

        Log.i(TAG, "MediaPlayerService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        lifecycleSupport.onStart(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        instance = null;

        reset();
        try
        {
            mediaPlayer.release();

            if (nextMediaPlayer != null)
            {
                nextMediaPlayer.release();
            }

            mediaPlayerLooper.quit();
            shufflePlayBuffer.shutdown();

            if (equalizerController != null)
            {
                equalizerController.release();
            }

            if (visualizerController != null)
            {
                visualizerController.release();
            }

            if (bufferTask != null)
            {
                bufferTask.cancel();
            }

            if (nextPlayingTask != null)
            {
                nextPlayingTask.cancel();
            }

            Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            sendBroadcast(i);

            audioManager.unregisterRemoteControlClient(remoteControlClient);
            clearRemoteControl();

            wakeLock.release();
        }
        catch (Throwable ignored)
        {
        }

        Log.i(TAG, "MediaPlayerService stopped");
    }

    public EqualizerController getEqualizerController()
    {
        if (equalizerAvailable && equalizerController == null)
        {
            equalizerController = new EqualizerController(this, mediaPlayer);
            if (!equalizerController.isAvailable())
            {
                equalizerController = null;
            }
            else
            {
                equalizerController.loadSettings();
            }
        }
        return equalizerController;
    }

    public VisualizerController getVisualizerController()
    {
        if (visualizerAvailable && visualizerController == null)
        {
            visualizerController = new VisualizerController(mediaPlayer);
            if (!visualizerController.isAvailable())
            {
                visualizerController = null;
            }
        }
        return visualizerController;
    }

    public void setUpRemoteControlClient()
    {
        if (!Util.isLockScreenEnabled(this)) return;

        ComponentName componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());

        if (remoteControlClient == null)
        {
            final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(componentName);
            PendingIntent broadcast = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteControlClient = new RemoteControlClient(broadcast);
            audioManager.registerRemoteControlClient(remoteControlClient);

            // Flags for the media transport control that this client supports.
            int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
                    RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
                    RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
                    RemoteControlClient.FLAG_KEY_MEDIA_STOP;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            {
                flags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;

                remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener()
                {
                    @Override
                    public long onGetPlaybackPosition()
                    {
                        return mediaPlayer.getCurrentPosition();
                    }
                });

                remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener()
                {
                    @Override
                    public void onPlaybackPositionUpdate(long newPositionMs)
                    {
                        seekTo((int) newPositionMs);
                    }
                });
            }

            remoteControlClient.setTransportControlFlags(flags);
        }
    }

    private void clearRemoteControl()
    {
        if (remoteControlClient != null)
        {
            remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            audioManager.unregisterRemoteControlClient(remoteControlClient);
            remoteControlClient = null;
        }
    }

    private void updateRemoteControl()
    {
        if (!Util.isLockScreenEnabled(this))
        {
            clearRemoteControl();
            return;
        }

        if (remoteControlClient != null)
        {
            audioManager.unregisterRemoteControlClient(remoteControlClient);
            audioManager.registerRemoteControlClient(remoteControlClient);
        }
        else
        {
            setUpRemoteControlClient();
        }

        Log.i(TAG, String.format("In updateRemoteControl, playerState: %s [%d]", playerState, getPlayerPosition()));

        switch (playerState)
        {
            case STARTED:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
                {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
                }
                else
                {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING, getPlayerPosition(), 1.0f);
                }
                break;
            default:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
                {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
                }
                else
                {
                    remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED, getPlayerPosition(), 1.0f);
                }
                break;
        }

        if (currentPlaying != null)
        {
            MusicDirectory.Entry currentSong = currentPlaying.getSong();

            Bitmap lockScreenBitmap = FileUtil.getAlbumArtBitmap(this, currentSong, Util.getMinDisplayMetric(this), true);

            String artist = currentSong.getArtist();
            String album = currentSong.getAlbum();
            String title = currentSong.getTitle();
            Integer currentSongDuration = currentSong.getDuration();
            Long duration = 0L;

            if (currentSongDuration != null) duration = (long) currentSongDuration * 1000;

            remoteControlClient.editMetadata(true).putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album).putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title).putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
                    .putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, lockScreenBitmap).apply();
        }
    }

    public synchronized void seekTo(int position)
    {
        try
        {
            if (jukeboxEnabled)
            {
                jukeboxService.skip(getCurrentPlayingIndex(), position / 1000);
            }
            else
            {
                mediaPlayer.seekTo(position);
                cachedPosition = position;

                updateRemoteControl();
            }
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    public synchronized int getPlayerPosition()
    {
        try
        {
            if (playerState == IDLE || playerState == DOWNLOADING || playerState == PREPARING)
            {
                return 0;
            }

            return jukeboxEnabled ? jukeboxService.getPositionSeconds() * 1000 : cachedPosition;
        }
        catch (Exception x)
        {
            handleError(x);
            return 0;
        }
    }

    public synchronized int getPlayerDuration()
    {
        if (MediaPlayerService.currentPlaying != null)
        {
            Integer duration = MediaPlayerService.currentPlaying.getSong().getDuration();
            if (duration != null)
            {
                return duration * 1000;
            }
        }
        if (playerState != IDLE && playerState != DOWNLOADING && playerState != PlayerState.PREPARING)
        {
            try
            {
                return mediaPlayer.getDuration();
            }
            catch (Exception x)
            {
                handleError(x);
            }
        }
        return 0;
    }

    public static synchronized int getCurrentPlayingIndex()
    {
        return downloadList.indexOf(currentPlaying);
    }

    public synchronized void setCurrentPlaying(int currentPlayingIndex)
    {
        try
        {
            setCurrentPlaying(downloadList.get(currentPlayingIndex));
        }
        catch (IndexOutOfBoundsException x)
        {
            // Ignored
        }
    }

    public synchronized void setCurrentPlaying(DownloadFile currentPlaying)
    {
        MediaPlayerService.currentPlaying = currentPlaying;

        if (currentPlaying != null)
        {
            Util.broadcastNewTrackInfo(this, currentPlaying.getSong());
            Util.broadcastA2dpPlayStatusChange(this, playerState, currentPlaying.getSong(), downloadList.size() + backgroundDownloadList.size(), downloadList.indexOf(currentPlaying) + 1, getPlayerPosition());
        }
        else
        {
            Util.broadcastNewTrackInfo(this, null);
            Util.broadcastA2dpMetaDataChange(this, null);
        }

        // Update widget
        UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);
        UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, true);
        UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);
        UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);

        updateRemoteControl();
        SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

        if (currentPlaying != null)
        {
            if (tabInstance != null) {
                updateNotification();
                tabInstance.showNowPlaying();
            }
        }
        else
        {
            if (tabInstance != null)
            {
                tabInstance.hideNowPlaying();
                stopForeground(true);
                isInForeground = false;
                stopSelf();
            }
        }
    }

    public synchronized void setNextPlaying()
    {
        boolean gaplessPlayback = Util.getGaplessPlaybackPreference(this);

        if (!gaplessPlayback)
        {
            nextPlaying = null;
            nextPlayerState = IDLE;
            return;
        }

        int index = getCurrentPlayingIndex();

        if (index != -1)
        {
            switch (getRepeatMode())
            {
                case OFF:
                    index += 1;
                    break;
                case ALL:
                    index = (index + 1) % size();
                    break;
                case SINGLE:
                    break;
                default:
                    break;
            }
        }

        nextSetup = false;
        if (nextPlayingTask != null)
        {
            nextPlayingTask.cancel();
            nextPlayingTask = null;
        }

        if (index < size() && index != -1)
        {
            nextPlaying = downloadList.get(index);
            nextPlayingTask = new CheckCompletionTask(nextPlaying);
            nextPlayingTask.start();
        }
        else
        {
            nextPlaying = null;
            setNextPlayerState(IDLE);
        }
    }

    public synchronized void togglePlayPause()
    {
        if (playerState == PAUSED || playerState == COMPLETED || playerState == STOPPED)
        {
            start();
        }
        else if (playerState == IDLE)
        {
            play();
        }
        else if (playerState == STARTED)
        {
            pause();
        }
    }

    public void setVolume(float volume)
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.setVolume(volume, volume);
        }
    }

    /**
     * Plays either the current song (resume) or the first/next one in queue.
     */
    public synchronized void play()
    {
        int current = getCurrentPlayingIndex();
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
        updateRemoteControl();

        if (index < 0 || index >= size())
        {
            resetPlayback();
        }
        else
        {
            if (nextPlayingTask != null)
            {
                nextPlayingTask.cancel();
                nextPlayingTask = null;
            }

            setCurrentPlaying(index);

            if (start)
            {
                if (jukeboxEnabled)
                {
                    jukeboxService.skip(getCurrentPlayingIndex(), 0);
                    setPlayerState(STARTED);
                }
                else
                {
                    bufferAndPlay();
                }
            }

            checkDownloads(this);
            setNextPlaying();
        }
    }

    private synchronized void resetPlayback()
    {
        reset();
        setCurrentPlaying(null);
        lifecycleSupport.serializeDownloadQueue();
    }

    public synchronized void reset()
    {
        if (bufferTask != null)
        {
            bufferTask.cancel();
        }
        try
        {
            setPlayerState(IDLE);
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.setOnCompletionListener(null);
            mediaPlayer.reset();
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    private synchronized void playNext()
    {
        MediaPlayer tmp = mediaPlayer;
        mediaPlayer = nextMediaPlayer;
        nextMediaPlayer = tmp;
        setCurrentPlaying(nextPlaying);
        setPlayerState(PlayerState.STARTED);
        setupHandlers(currentPlaying, false);
        setNextPlaying();

        // Proxy should not be being used here since the next player was already setup to play
        if (proxy != null)
        {
            proxy.stop();
            proxy = null;
        }
    }

    public synchronized void pause()
    {
        try
        {
            if (playerState == STARTED)
            {
                if (jukeboxEnabled)
                {
                    jukeboxService.stop();
                }
                else
                {
                    mediaPlayer.pause();
                }
                setPlayerState(PAUSED);
            }
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    public synchronized void stop()
    {
        try
        {
            if (playerState == STARTED)
            {
                if (jukeboxEnabled)
                {
                    jukeboxService.stop();
                }
                else
                {
                    mediaPlayer.pause();
                }
            }
            setPlayerState(STOPPED);
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    public synchronized void start()
    {
        try
        {
            if (jukeboxEnabled)
            {
                jukeboxService.start();
            }
            else
            {
                mediaPlayer.start();
            }
            setPlayerState(STARTED);
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    private synchronized void bufferAndPlay()
    {
        if (playerState != PREPARED)
        {
            reset();

            bufferTask = new BufferTask(currentPlaying, 0);
            bufferTask.start();
        }
        else
        {
            doPlay(currentPlaying, 0, true);
        }
    }

    public synchronized void setPlayerState(PlayerState playerState)
    {
        Log.i(TAG, String.format("%s -> %s (%s)", playerState.name(), playerState.name(), currentPlaying));

        MediaPlayerService.playerState = playerState;

        if (playerState == PAUSED)
        {
            lifecycleSupport.serializeDownloadQueue();
        }

        if (playerState == PlayerState.STARTED)
        {
            Util.requestAudioFocus(this);
        }

        boolean showWhenPaused = (playerState != PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(this));
        boolean show = playerState == PlayerState.STARTED || showWhenPaused;

        Util.broadcastPlaybackStatusChange(this, playerState);
        Util.broadcastA2dpPlayStatusChange(this, playerState, currentPlaying.getSong(), downloadList.size() + backgroundDownloadList.size(), downloadList.indexOf(currentPlaying) + 1, getPlayerPosition());

        if (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED)
        {
            // Set remote control
            updateRemoteControl();
        }

        // Update widget
        UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);
        UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, true);
        UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);
        UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, currentPlaying.getSong(), playerState == PlayerState.STARTED, false);
        SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

        if (show)
        {
            if (tabInstance != null)
            {
                // Only update notification is player state is one that will change the icon
                if (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED)
                {
                    updateNotification();
                    tabInstance.showNowPlaying();
                }
            }
        }
        else
        {
            if (tabInstance != null)
            {
                stopForeground(true);
                isInForeground = false;
                tabInstance.hideNowPlaying();
                stopSelf();
            }
        }

        if (playerState == STARTED)
        {
            scrobbler.scrobble(this, currentPlaying, false);
        }
        else if (playerState == COMPLETED)
        {
            scrobbler.scrobble(this, currentPlaying, true);
        }

        if (playerState == STARTED && positionCache == null)
        {
            positionCache = new PositionCache();
            Thread thread = new Thread(positionCache);
            thread.start();
        }
        else if (playerState != STARTED && positionCache != null)
        {
            positionCache.stop();
            positionCache = null;
        }
    }

    private void setPlayerStateCompleted()
    {
        Log.i(TAG, String.format("%s -> %s (%s)", playerState.name(), PlayerState.COMPLETED, currentPlaying));
        playerState = PlayerState.COMPLETED;

        if (positionCache != null)
        {
            positionCache.stop();
            positionCache = null;
        }

        scrobbler.scrobble(this, currentPlaying, true);
    }

    private static synchronized void setNextPlayerState(PlayerState playerState)
    {
        Log.i(TAG, String.format("Next: %s -> %s (%s)", nextPlayerState.name(), playerState.name(), nextPlaying));
        nextPlayerState = playerState;
    }

    public synchronized void doPlay(final DownloadFile downloadFile, final int position, final boolean start)
    {
        try
        {
            downloadFile.setPlaying(false);
            //downloadFile.setPlaying(true);
            final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();
            boolean partial = file.equals(downloadFile.getPartialFile());
            downloadFile.updateModificationDate();

            mediaPlayer.setOnCompletionListener(null);
            secondaryProgress = -1; // Ensure seeking in non StreamProxy playback works
            mediaPlayer.reset();
            setPlayerState(IDLE);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            String dataSource = file.getPath();

            if (partial)
            {
                if (proxy == null)
                {
                    proxy = new StreamProxy(new Supplier<DownloadFile>() {
                        @Override
                        public DownloadFile get() { return currentPlaying; }
                    });
                    proxy.start();
                }

                dataSource = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), URLEncoder.encode(dataSource, Constants.UTF_8));
                Log.i(TAG, String.format("Data Source: %s", dataSource));
            }
            else if (proxy != null)
            {
                proxy.stop();
                proxy = null;
            }

            Log.i(TAG, "Preparing media player");
            mediaPlayer.setDataSource(dataSource);
            setPlayerState(PREPARING);

            mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener()
            {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent)
                {
                    SeekBar progressBar = DownloadActivity.getProgressBar();
                    MusicDirectory.Entry song = downloadFile.getSong();

                    if (percent == 100)
                    {
                        if (progressBar != null)
                        {
                            progressBar.setSecondaryProgress(100 * progressBar.getMax());
                        }

                        mp.setOnBufferingUpdateListener(null);
                    }
                    else if (progressBar != null && song.getTranscodedContentType() == null && Util.getMaxBitRate(MediaPlayerService.this) == 0)
                    {
                        secondaryProgress = (int) (((double) percent / (double) 100) * progressBar.getMax());
                        progressBar.setSecondaryProgress(secondaryProgress);
                    }
                }
            });

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                @Override
                public void onPrepared(MediaPlayer mp)
                {
                    Log.i(TAG, "Media player prepared");

                    setPlayerState(PREPARED);

                    SeekBar progressBar = DownloadActivity.getProgressBar();

                    if (progressBar != null && downloadFile.isWorkDone())
                    {
                        // Populate seek bar secondary progress if we have a complete file for consistency
                        DownloadActivity.getProgressBar().setSecondaryProgress(100 * progressBar.getMax());
                    }

                    synchronized (MediaPlayerService.this)
                    {
                        if (position != 0)
                        {
                            Log.i(TAG, String.format("Restarting player from position %d", position));
                            seekTo(position);
                        }
                        cachedPosition = position;

                        if (start)
                        {
                            mediaPlayer.start();
                            setPlayerState(STARTED);
                        }
                        else
                        {
                            setPlayerState(PAUSED);
                        }
                    }

                    lifecycleSupport.serializeDownloadQueue();
                }
            });

            setupHandlers(downloadFile, partial);

            mediaPlayer.prepareAsync();
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    private synchronized void setupNext(final DownloadFile downloadFile)
    {
        try
        {
            final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();

            if (nextMediaPlayer != null)
            {
                nextMediaPlayer.setOnCompletionListener(null);
                nextMediaPlayer.release();
                nextMediaPlayer = null;
            }

            nextMediaPlayer = new MediaPlayer();
            nextMediaPlayer.setWakeMode(MediaPlayerService.this, PowerManager.PARTIAL_WAKE_LOCK);

            try
            {
                nextMediaPlayer.setAudioSessionId(mediaPlayer.getAudioSessionId());
            }
            catch (Throwable e)
            {
                nextMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            nextMediaPlayer.setDataSource(file.getPath());
            setNextPlayerState(PREPARING);

            nextMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                @Override
                @SuppressLint("NewApi")
                public void onPrepared(MediaPlayer mp)
                {
                    try
                    {
                        setNextPlayerState(PREPARED);

                        if (Util.getGaplessPlaybackPreference(MediaPlayerService.this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED))
                        {
                            mediaPlayer.setNextMediaPlayer(nextMediaPlayer);
                            nextSetup = true;
                        }
                    }
                    catch (Exception x)
                    {
                        handleErrorNext(x);
                    }
                }
            });

            nextMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
            {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int what, int extra)
                {
                    Log.w(TAG, String.format("Error on playing next (%d, %d): %s", what, extra, downloadFile));
                    return true;
                }
            });

            nextMediaPlayer.prepareAsync();
        }
        catch (Exception x)
        {
            handleErrorNext(x);
        }
    }

    private void setupHandlers(final DownloadFile downloadFile, final boolean isPartial)
    {
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
        {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra)
            {
                Log.w(TAG, String.format("Error on playing file (%d, %d): %s", what, extra, downloadFile));
                int pos = cachedPosition;
                reset();
                downloadFile.setPlaying(false);
                doPlay(downloadFile, pos, true);
                downloadFile.setPlaying(true);
                return true;
            }
        });

        final int duration = downloadFile.getSong().getDuration() == null ? 0 : downloadFile.getSong().getDuration() * 1000;

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer)
            {
                // Acquire a temporary wakelock, since when we return from
                // this callback the MediaPlayer will release its wakelock
                // and allow the device to go to sleep.
                wakeLock.acquire(60000);

                int pos = cachedPosition;
                Log.i(TAG, String.format("Ending position %d of %d", pos, duration));

                if (!isPartial || (downloadFile.isWorkDone() && (Math.abs(duration - pos) < 1000)))
                {
                    setPlayerStateCompleted();

                    if (Util.getGaplessPlaybackPreference(MediaPlayerService.this) && nextPlaying != null && nextPlayerState == PlayerState.PREPARED)
                    {
                        if (!nextSetup)
                        {
                            playNext();
                        }
                        else
                        {
                            nextSetup = false;
                            playNext();
                        }
                    }
                    else
                    {
                        onSongCompleted();
                    }

                    return;
                }

                synchronized (this)
                {
                    if (downloadFile.isWorkDone())
                    {
                        // Complete was called early even though file is fully buffered
                        Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
                        reset();
                        downloadFile.setPlaying(false);
                        doPlay(downloadFile, pos, true);
                        downloadFile.setPlaying(true);
                    }
                    else
                    {
                        Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
                        reset();
                        bufferTask = new BufferTask(downloadFile, pos);
                        bufferTask.start();
                    }
                }
            }
        });
    }

    private void onSongCompleted()
    {
        int index = getCurrentPlayingIndex();

        if (currentPlaying != null)
        {
            final MusicDirectory.Entry song = currentPlaying.getSong();

            if (song != null && song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(this))
            {
                MusicService musicService = MusicServiceFactory.getMusicService(this);
                try
                {
                    musicService.deleteBookmark(song.getId(), this, null);
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
                    if (index + 1 < 0 || index + 1 >= size())
                    {
                        if (Util.getShouldClearPlaylist(this))
                        {
                            clear(true);
                        }

                        resetPlayback();
                        break;
                    }

                    play(index + 1);
                    break;
                case ALL:
                    play((index + 1) % size());
                    break;
                case SINGLE:
                    play(index);
                    break;
                default:
                    break;
            }
        }
    }

    public static synchronized void clear(boolean serialize)
    {
        MediaPlayerService mediaPlayerService = getRunningInstance();

        if (mediaPlayerService != null) mediaPlayerService.reset();
        downloadList.clear();
        revision++;
        if (currentDownloading != null)
        {
            currentDownloading.cancelDownload();
            currentDownloading = null;
        }
        if (mediaPlayerService != null)
        {
            mediaPlayerService.setCurrentPlaying(null);
            updateJukeboxPlaylist();
            mediaPlayerService.setNextPlaying();
        }

        if (serialize)
        {
            lifecycleSupport.serializeDownloadQueue();
        }
    }

    protected static synchronized void checkDownloads(Context context)
    {
        if (!Util.isExternalStoragePresent() || !lifecycleSupport.isExternalStorageAvailable())
        {
            return;
        }

        if (shufflePlay)
        {
            checkShufflePlay(context);
        }

        if (jukeboxEnabled || !Util.isNetworkConnected(context))
        {
            return;
        }

        if (MediaPlayerService.downloadList.isEmpty() && MediaPlayerService.backgroundDownloadList.isEmpty())
        {
            return;
        }

        // Need to download current playing?
        if (MediaPlayerService.currentPlaying != null && MediaPlayerService.currentPlaying != MediaPlayerService.currentDownloading && !MediaPlayerService.currentPlaying.isWorkDone())
        {
            // Cancel current download, if necessary.
            if (MediaPlayerService.currentDownloading != null)
            {
                MediaPlayerService.currentDownloading.cancelDownload();
            }

            MediaPlayerService.currentDownloading = MediaPlayerService.currentPlaying;
            MediaPlayerService.currentDownloading.download();
            cleanupCandidates.add(MediaPlayerService.currentDownloading);
        }

        // Find a suitable target for download.
        else
        {
            if (MediaPlayerService.currentDownloading == null ||
                    MediaPlayerService.currentDownloading.isWorkDone() ||
                    MediaPlayerService.currentDownloading.isFailed() &&
                            (!MediaPlayerService.downloadList.isEmpty() || !MediaPlayerService.backgroundDownloadList.isEmpty()))
            {
                MediaPlayerService.currentDownloading = null;
                int n = size();

                int preloaded = 0;

                if (n != 0)
                {
                    int start = MediaPlayerService.currentPlaying == null ? 0 : getCurrentPlayingIndex();
                    if (start == -1)
                    {
                        start = 0;
                    }
                    int i = start;
                    do
                    {
                        DownloadFile downloadFile = MediaPlayerService.downloadList.get(i);
                        if (!downloadFile.isWorkDone())
                        {
                            if (downloadFile.shouldSave() || preloaded < Util.getPreloadCount(context))
                            {
                                MediaPlayerService.currentDownloading = downloadFile;
                                MediaPlayerService.currentDownloading.download();
                                cleanupCandidates.add(MediaPlayerService.currentDownloading);
                                if (i == (start + 1))
                                {
                                    setNextPlayerState(DOWNLOADING);
                                }
                                break;
                            }
                        }
                        else if (MediaPlayerService.currentPlaying != downloadFile)
                        {
                            preloaded++;
                        }

                        i = (i + 1) % n;
                    } while (i != start);
                }

                if ((preloaded + 1 == n || preloaded >= Util.getPreloadCount(context) || MediaPlayerService.downloadList.isEmpty()) && !MediaPlayerService.backgroundDownloadList.isEmpty())
                {
                    for (int i = 0; i < MediaPlayerService.backgroundDownloadList.size(); i++)
                    {
                        DownloadFile downloadFile = MediaPlayerService.backgroundDownloadList.get(i);
                        if (downloadFile.isWorkDone() && (!downloadFile.shouldSave() || downloadFile.isSaved()))
                        {
                            if (Util.getShouldScanMedia(context))
                            {
                                Util.scanMedia(context, downloadFile.getCompleteFile());
                            }

                            // Don't need to keep list like active song list
                            MediaPlayerService.backgroundDownloadList.remove(i);
                            revision++;
                            i--;
                        }
                        else
                        {
                            MediaPlayerService.currentDownloading = downloadFile;
                            MediaPlayerService.currentDownloading.download();
                            cleanupCandidates.add(MediaPlayerService.currentDownloading);
                            break;
                        }
                    }
                }
            }
        }

        // Delete obsolete .partial and .complete files.
        cleanup(context);
    }

    private static synchronized void checkShufflePlay(Context context)
    {
        // Get users desired random playlist size
        int listSize = Util.getMaxSongs(context);
        boolean wasEmpty = MediaPlayerService.downloadList.isEmpty();

        long revisionBefore = revision;

        // First, ensure that list is at least 20 songs long.
        int size = size();
        if (size < listSize)
        {
            for (MusicDirectory.Entry song : MediaPlayerService.shufflePlayBuffer.get(listSize - size))
            {
                DownloadFile downloadFile = new DownloadFile(context, song, false);
                MediaPlayerService.downloadList.add(downloadFile);
                revision++;
            }
        }

        int currIndex = MediaPlayerService.currentPlaying == null ? 0 : getCurrentPlayingIndex();

        // Only shift playlist if playing song #5 or later.
        if (currIndex > 4)
        {
            int songsToShift = currIndex - 2;
            for (MusicDirectory.Entry song : MediaPlayerService.shufflePlayBuffer.get(songsToShift))
            {
                MediaPlayerService.downloadList.add(new DownloadFile(context, song, false));
                MediaPlayerService.downloadList.get(0).cancelDownload();
                MediaPlayerService.downloadList.remove(0);
                revision++;
            }
        }

        if (revisionBefore != revision)
        {
            getInstance(context).updateJukeboxPlaylist();
        }

        if (wasEmpty && !MediaPlayerService.downloadList.isEmpty())
        {
            getInstance(context).play(0);
        }
    }

    public static void updateJukeboxPlaylist()
    {
        if (jukeboxEnabled)
        {
            jukeboxService.updatePlaylist();
        }
    }

    private static synchronized void cleanup(Context context)
    {
        Iterator<DownloadFile> iterator = cleanupCandidates.iterator();
        while (iterator.hasNext())
        {
            DownloadFile downloadFile = iterator.next();
            if (downloadFile != MediaPlayerService.currentPlaying && downloadFile != MediaPlayerService.currentDownloading)
            {
                if (downloadFile.cleanup())
                {
                    iterator.remove();
                }
            }
        }
    }

    public void updateNotification()
    {
        if (Util.isNotificationEnabled(this)) {
            if (isInForeground == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification());
                }
                else {
                    final NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(this);
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification());
                }
                Log.w(TAG, "--- Updated notification");
            }
            else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification());
                isInForeground = true;
                Log.w(TAG, "--- Created Foreground notification");
            }
        }
    }

    @SuppressWarnings("IconColors")
    private Notification buildForegroundNotification() {
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

    private class PositionCache implements Runnable
    {
        boolean isRunning = true;

        public void stop()
        {
            isRunning = false;
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("PositionCache");

            // Stop checking position before the song reaches completion
            while (isRunning)
            {
                try
                {
                    if (mediaPlayer != null && playerState == STARTED)
                    {
                        cachedPosition = mediaPlayer.getCurrentPosition();
                    }

                    Util.sleepQuietly(25L);
                }
                catch (Exception e)
                {
                    Log.w(TAG, "Crashed getting current position", e);
                    isRunning = false;
                    positionCache = null;
                }
            }
        }
    }

    private void handleError(Exception x)
    {
        Log.w(TAG, String.format("Media player error: %s", x), x);

        try
        {
            mediaPlayer.reset();
        }
        catch (Exception ex)
        {
            Log.w(TAG, String.format("Exception encountered when resetting media player: %s", ex), ex);
        }
    }

    private void handleErrorNext(Exception x)
    {
        Log.w(TAG, String.format("Next Media player error: %s", x), x);
        nextMediaPlayer.reset();
    }

    private class BufferTask extends CancellableTask
    {
        private final DownloadFile downloadFile;
        private final int position;
        private final long expectedFileSize;
        private final File partialFile;

        public BufferTask(DownloadFile downloadFile, int position)
        {
            this.downloadFile = downloadFile;
            this.position = position;
            partialFile = downloadFile.getPartialFile();

            long bufferLength = Util.getBufferLength(MediaPlayerService.this);

            if (bufferLength == 0)
            {
                // Set to seconds in a day, basically infinity
                bufferLength = 86400L;
            }

            // Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
            int bitRate = downloadFile.getBitRate();
            long byteCount = Math.max(100000, bitRate * 1024L / 8L * bufferLength);

            // Find out how large the file should grow before resuming playback.
            Log.i(TAG, String.format("Buffering from position %d and bitrate %d", position, bitRate));
            expectedFileSize = (position * bitRate / 8) + byteCount;
        }

        @Override
        public void execute()
        {
            setPlayerState(DOWNLOADING);

            while (!bufferComplete() && !Util.isOffline(MediaPlayerService.this))
            {
                Util.sleepQuietly(1000L);
                if (isCancelled())
                {
                    return;
                }
            }
            doPlay(downloadFile, position, true);
        }

        private boolean bufferComplete()
        {
            boolean completeFileAvailable = downloadFile.isWorkDone();
            long size = partialFile.length();

            Log.i(TAG, String.format("Buffering %s (%d/%d, %s)", partialFile, size, expectedFileSize, completeFileAvailable));
            return completeFileAvailable || size >= expectedFileSize;
        }

        @Override
        public String toString()
        {
            return String.format("BufferTask (%s)", downloadFile);
        }
    }

    private class CheckCompletionTask extends CancellableTask
    {
        private final DownloadFile downloadFile;
        private final File partialFile;

        public CheckCompletionTask(DownloadFile downloadFile)
        {
            super();
            setNextPlayerState(PlayerState.IDLE);

            this.downloadFile = downloadFile;

            partialFile = downloadFile != null ? downloadFile.getPartialFile() : null;
        }

        @Override
        public void execute()
        {
            Thread.currentThread().setName("CheckCompletionTask");

            if (downloadFile == null)
            {
                return;
            }

            // Do an initial sleep so this prepare can't compete with main prepare
            Util.sleepQuietly(5000L);

            while (!bufferComplete())
            {
                Util.sleepQuietly(5000L);

                if (isCancelled())
                {
                    return;
                }
            }

            // Start the setup of the next media player
            mediaPlayerHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    setupNext(downloadFile);
                }
            });
        }

        private boolean bufferComplete()
        {
            boolean completeFileAvailable = downloadFile.isWorkDone();
            Log.i(TAG, String.format("Buffering next %s (%d)", partialFile, partialFile.length()));
            return completeFileAvailable && (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED);
        }

        @Override
        public String toString()
        {
            return String.format("CheckCompletionTask (%s)", downloadFile);
        }

    }
}
