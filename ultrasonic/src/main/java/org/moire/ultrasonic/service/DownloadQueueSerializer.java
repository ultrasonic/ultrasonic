package org.moire.ultrasonic.service;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is responsible for the serialization / deserialization
 * of the DownloadQueue (playlist) to the filesystem.
 * It also serializes the player state e.g. current playing number and play position.
 */
public class DownloadQueueSerializer
{
    private static final String TAG = DownloadQueueSerializer.class.getSimpleName();

    public final Lock lock = new ReentrantLock();
    public final AtomicBoolean setup = new AtomicBoolean(false);
    private Context context;

    public DownloadQueueSerializer(Context context)
    {
        this.context = context;
    }

    public void serializeDownloadQueue(Iterable<DownloadFile> songs, int currentPlayingIndex, int currentPlayingPosition)
    {
        if (!setup.get())
        {
            return;
        }

        new SerializeTask().execute(songs, currentPlayingIndex, currentPlayingPosition);
    }

    public void serializeDownloadQueueNow(Iterable<DownloadFile> songs, int currentPlayingIndex, int currentPlayingPosition)
    {
        State state = new State();
        for (DownloadFile downloadFile : songs)
        {
            state.songs.add(downloadFile.getSong());
        }
        state.currentPlayingIndex = currentPlayingIndex;
        state.currentPlayingPosition = currentPlayingPosition;

        Log.i(TAG, String.format("Serialized currentPlayingIndex: %d, currentPlayingPosition: %d", state.currentPlayingIndex, state.currentPlayingPosition));
        FileUtil.serialize(context, state, Constants.FILENAME_DOWNLOADS_SER);
    }

    public void deserializeDownloadQueue(Consumer<State> afterDeserialized)
    {
        new DeserializeTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, afterDeserialized);
    }

    public void deserializeDownloadQueueNow(Consumer<State> afterDeserialized)
    {
        State state = FileUtil.deserialize(context, Constants.FILENAME_DOWNLOADS_SER);
        if (state == null) return;
        Log.i(TAG, "Deserialized currentPlayingIndex: " + state.currentPlayingIndex + ", currentPlayingPosition: " + state.currentPlayingPosition);
        afterDeserialized.accept(state);
    }

    private class SerializeTask extends AsyncTask<Object, Void, Void>
    {
        @Override
        protected Void doInBackground(Object... params)
        {
            if (lock.tryLock())
            {
                try
                {
                    Thread.currentThread().setName("SerializeTask");
                    serializeDownloadQueueNow((Iterable<DownloadFile>)params[0], (int)params[1], (int)params[2]);
                }
                finally
                {
                    lock.unlock();
                }
            }
            return null;
        }
    }

    private class DeserializeTask extends AsyncTask<Object, Void, Void>
    {
        @Override
        protected Void doInBackground(Object... params)
        {
            try
            {
                Thread.currentThread().setName("DeserializeTask");
                lock.lock();
                deserializeDownloadQueueNow((Consumer<State>)params[0]);
                setup.set(true);
            }
            finally
            {
                lock.unlock();
            }

            return null;
        }
    }
}
