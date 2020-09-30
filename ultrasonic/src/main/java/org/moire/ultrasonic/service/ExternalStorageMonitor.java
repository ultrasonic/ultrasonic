package org.moire.ultrasonic.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import timber.log.Timber;

/**
 * Monitors the state of the mobile's external storage
 */
public class ExternalStorageMonitor
{
    private Context context;
    private BroadcastReceiver ejectEventReceiver;
    private boolean externalStorageAvailable = true;

    public ExternalStorageMonitor(Context context)
    {
        this.context = context;
    }

    public void onCreate(final Runnable ejectedCallback)
    {
        // Stop when SD card is ejected.
        ejectEventReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                externalStorageAvailable = Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction());
                if (!externalStorageAvailable)
                {
                    Timber.i("External media is ejecting. Stopping playback.");
                    ejectedCallback.run();
                }
                else
                {
                    Timber.i("External media is available.");
                }
            }
        };

        IntentFilter ejectFilter = new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        ejectFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        ejectFilter.addDataScheme("file");
        context.registerReceiver(ejectEventReceiver, ejectFilter);
    }

    public void onDestroy()
    {
        context.unregisterReceiver(ejectEventReceiver);
    }

    public boolean isExternalStorageAvailable() { return externalStorageAvailable; }
}
