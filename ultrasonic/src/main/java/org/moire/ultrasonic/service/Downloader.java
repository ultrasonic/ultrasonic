package org.moire.ultrasonic.service;

import android.content.Context;
import android.util.Log;

import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.util.LRUCache;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.STARTED;

public class Downloader
{
    private static final String TAG = Downloader.class.getSimpleName();

    private final ShufflePlayBuffer shufflePlayBuffer;
    private final ExternalStorageMonitor externalStorageMonitor;
    private final Player player;
    public Lazy<JukeboxService> jukeboxService = inject(JukeboxService.class);

    public final List<DownloadFile> downloadList = new ArrayList<>();
    public final List<DownloadFile> backgroundDownloadList = new ArrayList<>();
    private final List<DownloadFile> cleanupCandidates = new ArrayList<>();
    private final LRUCache<MusicDirectory.Entry, DownloadFile> downloadFileCache = new LRUCache<>(100);

    public DownloadFile currentDownloading;
    public static long revision;

    private ScheduledExecutorService executorService;
    private Context context;

    public Downloader(Context context, ShufflePlayBuffer shufflePlayBuffer, ExternalStorageMonitor externalStorageMonitor,
                      Player player)
    {
        this.context = context;
        this.shufflePlayBuffer = shufflePlayBuffer;
        this.externalStorageMonitor = externalStorageMonitor;
        this.player = player;
    }

    public void onCreate()
    {
        Runnable downloadChecker = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    checkDownloads();
                }
                catch (Throwable x)
                {
                    Log.e(TAG, "checkDownloads() failed.", x);
                }
            }
        };

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(downloadChecker, 5, 5, TimeUnit.SECONDS);
        Log.i(TAG, "Downloader created");

    }

    public void onDestroy()
    {
        executorService.shutdown();
        Log.i(TAG, "Downloader destroyed");
    }

    protected synchronized void checkDownloads()
    {
        if (!Util.isExternalStoragePresent() || !externalStorageMonitor.isExternalStorageAvailable())
        {
            return;
        }

        if (shufflePlayBuffer.isEnabled)
        {
            checkShufflePlay(context);
        }

        if (jukeboxService.getValue().isEnabled() || !Util.isNetworkConnected(context))
        {
            return;
        }

        if (downloadList.isEmpty() && backgroundDownloadList.isEmpty())
        {
            return;
        }

        // Need to download current playing?
        if (player.currentPlaying != null && player.currentPlaying != currentDownloading && !player.currentPlaying.isWorkDone())
        {
            // Cancel current download, if necessary.
            if (currentDownloading != null)
            {
                currentDownloading.cancelDownload();
            }

            currentDownloading = player.currentPlaying;
            currentDownloading.download();
            cleanupCandidates.add(currentDownloading);

            // Delete obsolete .partial and .complete files.
            cleanup();
            return;
        }

        // Find a suitable target for download.
        if (currentDownloading != null &&
                !currentDownloading.isWorkDone() &&
                (!currentDownloading.isFailed() || (downloadList.isEmpty() && backgroundDownloadList.isEmpty())))
        {
            cleanup();
            return;
        }

        // There is a target to download
        currentDownloading = null;
        int n = downloadList.size();

        int preloaded = 0;

        if (n != 0)
        {
            int start = player.currentPlaying == null ? 0 : getCurrentPlayingIndex();
            if (start == -1) start = 0;

            int i = start;
            do
            {
                DownloadFile downloadFile = downloadList.get(i);
                if (!downloadFile.isWorkDone())
                {
                    if (downloadFile.shouldSave() || preloaded < Util.getPreloadCount(context))
                    {
                        currentDownloading = downloadFile;
                        currentDownloading.download();
                        cleanupCandidates.add(currentDownloading);
                        if (i == (start + 1))
                        {
                            player.setNextPlayerState(DOWNLOADING);
                        }
                        break;
                    }
                }
                else if (player.currentPlaying != downloadFile)
                {
                    preloaded++;
                }

                i = (i + 1) % n;
            } while (i != start);
        }

        if ((preloaded + 1 == n || preloaded >= Util.getPreloadCount(context) || downloadList.isEmpty()) && !backgroundDownloadList.isEmpty())
        {
            for (int i = 0; i < backgroundDownloadList.size(); i++)
            {
                DownloadFile downloadFile = backgroundDownloadList.get(i);
                if (downloadFile.isWorkDone() && (!downloadFile.shouldSave() || downloadFile.isSaved()))
                {
                    if (Util.getShouldScanMedia(context))
                    {
                        Util.scanMedia(context, downloadFile.getCompleteFile());
                    }

                    // Don't need to keep list like active song list
                    backgroundDownloadList.remove(i);
                    revision++;
                    i--;
                }
                else
                {
                    currentDownloading = downloadFile;
                    currentDownloading.download();
                    cleanupCandidates.add(currentDownloading);
                    break;
                }
            }
        }

        // Delete obsolete .partial and .complete files.
        cleanup();
    }

    public synchronized int getCurrentPlayingIndex()
    {
        return downloadList.indexOf(player.currentPlaying);
    }

    public long getDownloadListDuration()
    {
        long totalDuration = 0;

        for (DownloadFile downloadFile : downloadList)
        {
            MusicDirectory.Entry entry = downloadFile.getSong();

            if (!entry.isDirectory())
            {
                if (entry.getArtist() != null)
                {
                    Integer duration = entry.getDuration();

                    if (duration != null)
                    {
                        totalDuration += duration;
                    }
                }
            }
        }

        return totalDuration;
    }

    public synchronized List<DownloadFile> getDownloads()
    {
        List<DownloadFile> temp = new ArrayList<>();
        temp.addAll(downloadList);
        temp.addAll(backgroundDownloadList);
        return temp;
    }

    public List<DownloadFile> getBackgroundDownloads()
    {
        return backgroundDownloadList;
    }

    public long getDownloadListUpdateRevision()
    {
        return revision;
    }

    public synchronized void clear()
    {
        downloadList.clear();
        revision++;
        if (currentDownloading != null)
        {
            currentDownloading.cancelDownload();
            currentDownloading = null;
        }
    }

    public synchronized void clearBackground()
    {
        if (currentDownloading != null && backgroundDownloadList.contains(currentDownloading))
        {
            currentDownloading.cancelDownload();
            currentDownloading = null;
        }
        backgroundDownloadList.clear();
    }

    public synchronized void removeDownloadFile(DownloadFile downloadFile)
    {
        if (downloadFile == currentDownloading)
        {
            currentDownloading.cancelDownload();
            currentDownloading = null;
        }

        downloadList.remove(downloadFile);
        backgroundDownloadList.remove(downloadFile);
        revision++;
    }

    public synchronized void download(List<MusicDirectory.Entry> songs, boolean save, boolean autoPlay, boolean playNext, boolean newPlaylist)
    {
		shufflePlayBuffer.isEnabled = false;
        int offset = 1;

        if (songs.isEmpty())
        {
            return;
        }

        if (newPlaylist)
        {
            downloadList.clear();
        }

        if (playNext)
        {
            if (autoPlay && getCurrentPlayingIndex() >= 0)
            {
                offset = 0;
            }

            for (MusicDirectory.Entry song : songs)
            {
                DownloadFile downloadFile = new DownloadFile(context, song, save);
                downloadList.add(getCurrentPlayingIndex() + offset, downloadFile);
                offset++;
            }
        }
        else
        {
            for (MusicDirectory.Entry song : songs)
            {
                DownloadFile downloadFile = new DownloadFile(context, song, save);
                downloadList.add(downloadFile);
            }
        }
        revision++;
    }

    public synchronized void downloadBackground(List<MusicDirectory.Entry> songs, boolean save)
    {
        for (MusicDirectory.Entry song : songs)
        {
            DownloadFile downloadFile = new DownloadFile(context, song, save);
            backgroundDownloadList.add(downloadFile);
        }

        revision++;

        checkDownloads();
    }

    public synchronized void shuffle()
    {
        Collections.shuffle(downloadList);
        if (player.currentPlaying != null)
        {
            downloadList.remove(getCurrentPlayingIndex());
            downloadList.add(0, player.currentPlaying);
        }
        revision++;
    }

    public synchronized void setFirstPlaying()
    {
        if (player.currentPlaying == null)
        {
            player.currentPlaying = downloadList.get(0);
            player.currentPlaying.setPlaying(true);
        }

        checkDownloads();
    }

    public synchronized DownloadFile getDownloadFileForSong(MusicDirectory.Entry song)
    {
        for (DownloadFile downloadFile : downloadList)
        {
            if (downloadFile.getSong().equals(song) && ((downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && downloadFile.getPartialFile().exists()) || downloadFile.isWorkDone()))
            {
                return downloadFile;
            }
        }
        for (DownloadFile downloadFile : backgroundDownloadList)
        {
            if (downloadFile.getSong().equals(song))
            {
                return downloadFile;
            }
        }

        DownloadFile downloadFile = downloadFileCache.get(song);
        if (downloadFile == null)
        {
            downloadFile = new DownloadFile(context, song, false);
            downloadFileCache.put(song, downloadFile);
        }
        return downloadFile;
    }

    private synchronized void cleanup()
    {
        Iterator<DownloadFile> iterator = cleanupCandidates.iterator();
        while (iterator.hasNext())
        {
            DownloadFile downloadFile = iterator.next();
            if (downloadFile != player.currentPlaying && downloadFile != currentDownloading)
            {
                if (downloadFile.cleanup())
                {
                    iterator.remove();
                }
            }
        }
    }

    private synchronized void checkShufflePlay(Context context)
    {
        // Get users desired random playlist size
        int listSize = Util.getMaxSongs(context);
        boolean wasEmpty = downloadList.isEmpty();

        long revisionBefore = revision;

        // First, ensure that list is at least 20 songs long.
        int size = downloadList.size();
        if (size < listSize)
        {
            for (MusicDirectory.Entry song : shufflePlayBuffer.get(listSize - size))
            {
                DownloadFile downloadFile = new DownloadFile(context, song, false);
                downloadList.add(downloadFile);
                revision++;
            }
        }

        int currIndex = player.currentPlaying == null ? 0 : getCurrentPlayingIndex();

        // Only shift playlist if playing song #5 or later.
        if (currIndex > 4)
        {
            int songsToShift = currIndex - 2;
            for (MusicDirectory.Entry song : shufflePlayBuffer.get(songsToShift))
            {
                downloadList.add(new DownloadFile(context, song, false));
                downloadList.get(0).cancelDownload();
                downloadList.remove(0);
                revision++;
            }
        }

        if (revisionBefore != revision)
        {
            jukeboxService.getValue().updatePlaylist();
        }

        if (wasEmpty && !downloadList.isEmpty())
        {
            if (jukeboxService.getValue().isEnabled())
            {
                jukeboxService.getValue().skip(0, 0);
                player.setPlayerState(STARTED);
            }
            else
            {
                player.play(downloadList.get(0));
            }
        }
    }
}
