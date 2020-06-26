package org.moire.ultrasonic.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
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
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.SeekBar;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.activity.DownloadActivity;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.StreamProxy;
import org.moire.ultrasonic.util.Util;

import java.io.File;
import java.net.URLEncoder;
import java.util.Locale;

import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARING;
import static org.moire.ultrasonic.domain.PlayerState.STARTED;

/**
 * Represents a Media Player which uses the mobile's resources for playback
 */
public class LocalMediaPlayer
{
    private static final String TAG = LocalMediaPlayer.class.getSimpleName();

    public Consumer<DownloadFile> onCurrentPlayingChanged;
    public Consumer<DownloadFile> onSongCompleted;
    public BiConsumer<PlayerState, DownloadFile> onPlayerStateChanged;
    public Runnable onPrepared;
    public Runnable onNextSongRequested;

    public static boolean equalizerAvailable;
    public static boolean visualizerAvailable;

    public PlayerState playerState = IDLE;
    public DownloadFile currentPlaying;
    public DownloadFile nextPlaying;

    private PlayerState nextPlayerState = IDLE;
    private boolean nextSetup;
    private CancellableTask nextPlayingTask;
    private PowerManager.WakeLock wakeLock;

    private MediaPlayer mediaPlayer;
    private MediaPlayer nextMediaPlayer;
    private Looper mediaPlayerLooper;
    private Handler mediaPlayerHandler;
    private int cachedPosition;
    private StreamProxy proxy;

    private AudioManager audioManager;
    private RemoteControlClient remoteControlClient;

    private EqualizerController equalizerController;
    private VisualizerController visualizerController;
    private CancellableTask bufferTask;
    private PositionCache positionCache;
    private int secondaryProgress = -1;

    private final Context context;

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

    public LocalMediaPlayer(Context context)
    {
        this.context = context;
    }

    public void onCreate()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("MediaPlayerThread");

                Looper.prepare();

                if (mediaPlayer != null)
                {
                    mediaPlayer.release();
                }

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
                {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int what, int more)
                    {
                        handleError(new Exception(String.format(Locale.getDefault(), "MediaPlayer error: %d (%d)", what, more)));
                        return false;
                    }
                });

                try
                {
                    Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
                    i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
                    context.sendBroadcast(i);
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

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        wakeLock.setReferenceCounted(false);

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        setUpRemoteControlClient();

        if (equalizerAvailable)
        {
            equalizerController = new EqualizerController(context, mediaPlayer);
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

        Log.i(TAG, "LocalMediaPlayer created");
    }

    public void onDestroy()
    {
        reset();

        try
        {
            mediaPlayer.release();
            if (nextMediaPlayer != null)
            {
                nextMediaPlayer.release();
            }

            mediaPlayerLooper.quit();

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
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.sendBroadcast(i);

            audioManager.unregisterRemoteControlClient(remoteControlClient);
            clearRemoteControl();
            wakeLock.release();
        }
        catch (Throwable ignored)
        {
        }

        Log.i(TAG, "LocalMediaPlayer destroyed");
    }

    public EqualizerController getEqualizerController()
    {
        if (equalizerAvailable && equalizerController == null)
        {
            equalizerController = new EqualizerController(context, mediaPlayer);
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

    public synchronized void setPlayerState(final PlayerState playerState)
    {
        Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), playerState.name(), currentPlaying));

        this.playerState = playerState;

        if (playerState == PlayerState.STARTED)
        {
            Util.requestAudioFocus(context);
        }

        if (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED)
        {
            updateRemoteControl();
        }

        if (onPlayerStateChanged != null)
        {
            Handler mainHandler = new Handler(context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    onPlayerStateChanged.accept(playerState, currentPlaying);
                }
            };
            mainHandler.post(myRunnable);
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

    public synchronized void setCurrentPlaying(final DownloadFile currentPlaying)
    {
        Log.v(TAG, String.format("setCurrentPlaying %s", currentPlaying));
        this.currentPlaying = currentPlaying;
        updateRemoteControl();

        if (onCurrentPlayingChanged != null)
        {
            Handler mainHandler = new Handler(context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    onCurrentPlayingChanged.accept(currentPlaying);
                }
            };
            mainHandler.post(myRunnable);
        }
    }

    public synchronized void setNextPlaying(DownloadFile nextToPlay)
    {
        if (nextToPlay == null)
        {
            nextPlaying = null;
            setNextPlayerState(IDLE);
            return;
        }

        nextPlaying = nextToPlay;
        nextPlayingTask = new CheckCompletionTask(nextPlaying);
        nextPlayingTask.start();
    }

    public synchronized void clearNextPlaying()
    {
        nextSetup = false;
        nextPlaying = null;
        if (nextPlayingTask != null)
        {
            nextPlayingTask.cancel();
            nextPlayingTask = null;
        }
    }

    public synchronized void setNextPlayerState(PlayerState playerState)
    {
        Log.i(TAG, String.format("Next: %s -> %s (%s)", nextPlayerState.name(), playerState.name(), nextPlaying));
        nextPlayerState = playerState;
    }

    public synchronized void bufferAndPlay()
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

    public synchronized void play(DownloadFile fileToPlay)
    {
        if (nextPlayingTask != null)
        {
            nextPlayingTask.cancel();
            nextPlayingTask = null;
        }

        setCurrentPlaying(fileToPlay);
        bufferAndPlay();
    }

    public synchronized void playNext()
    {
        MediaPlayer tmp = mediaPlayer;
        mediaPlayer = nextMediaPlayer;
        nextMediaPlayer = tmp;
        setCurrentPlaying(nextPlaying);
        setPlayerState(PlayerState.STARTED);
        setupHandlers(currentPlaying, false);

        if (onNextSongRequested != null) {
            Handler mainHandler = new Handler(context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    onNextSongRequested.run();
                }
            };
            mainHandler.post(myRunnable);
        }

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
            mediaPlayer.pause();
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
            mediaPlayer.start();
        }
        catch (Exception x)
        {
            handleError(x);
        }
    }

    private void updateRemoteControl()
    {
        if (!Util.isLockScreenEnabled(context))
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

        if (playerState == STARTED) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
            } else {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING, getPlayerPosition(), 1.0f);
            }
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
            } else {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED, getPlayerPosition(), 1.0f);
            }
        }

        if (currentPlaying != null)
        {
            MusicDirectory.Entry currentSong = currentPlaying.getSong();

            Bitmap lockScreenBitmap = FileUtil.getAlbumArtBitmap(context, currentSong, Util.getMinDisplayMetric(context), true);

            String artist = currentSong.getArtist();
            String album = currentSong.getAlbum();
            String title = currentSong.getTitle();
            Integer currentSongDuration = currentSong.getDuration();
            long duration = 0L;

            if (currentSongDuration != null) duration = (long) currentSongDuration * 1000;

            remoteControlClient.editMetadata(true)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artist)
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
                    .putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, lockScreenBitmap)
                    .apply();
        }
    }

    public void clearRemoteControl()
    {
        if (remoteControlClient != null)
        {
            remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
            audioManager.unregisterRemoteControlClient(remoteControlClient);
            remoteControlClient = null;
        }
    }

    private void setUpRemoteControlClient()
    {
        if (!Util.isLockScreenEnabled(context)) return;

        ComponentName componentName = new ComponentName(context.getPackageName(), MediaButtonIntentReceiver.class.getName());

        if (remoteControlClient == null)
        {
            final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(componentName);
            PendingIntent broadcast = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
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

    public synchronized void seekTo(int position)
    {
        try
        {
            mediaPlayer.seekTo(position);
            cachedPosition = position;

            updateRemoteControl();
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

            return cachedPosition;
        }
        catch (Exception x)
        {
            handleError(x);
            return 0;
        }
    }

    public synchronized int getPlayerDuration()
    {
        if (currentPlaying != null)
        {
            Integer duration = currentPlaying.getSong().getDuration();
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

    public void setVolume(float volume)
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.setVolume(volume, volume);
        }
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

                dataSource = String.format(Locale.getDefault(), "http://127.0.0.1:%d/%s",
                        proxy.getPort(), URLEncoder.encode(dataSource, Constants.UTF_8));
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
                    else if (progressBar != null && song.getTranscodedContentType() == null && Util.getMaxBitRate(context) == 0)
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

                    synchronized (LocalMediaPlayer.this)
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

                    if (onPrepared != null) {
                        Handler mainHandler = new Handler(context.getMainLooper());
                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run() {
                                onPrepared.run();
                            }
                        };
                        mainHandler.post(myRunnable);
                    }
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
            nextMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

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

                        if (Util.getGaplessPlaybackPreference(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED))
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
                    setPlayerState(COMPLETED);

                    if (Util.getGaplessPlaybackPreference(context) && nextPlaying != null && nextPlayerState == PlayerState.PREPARED)
                    {
                        if (nextSetup)
                        {
                            nextSetup = false;
                        }
                        playNext();
                    }
                    else
                    {
                        if (onSongCompleted != null)
                        {
                            Handler mainHandler = new Handler(context.getMainLooper());
                            Runnable myRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    onSongCompleted.accept(currentPlaying);
                                }
                            };
                            mainHandler.post(myRunnable);
                        }
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

            long bufferLength = Util.getBufferLength(context);

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

            while (!bufferComplete() && !Util.isOffline(context))
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

        @NotNull
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

        @NotNull
        @Override
        public String toString()
        {
            return String.format("CheckCompletionTask (%s)", downloadFile);
        }

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
}
