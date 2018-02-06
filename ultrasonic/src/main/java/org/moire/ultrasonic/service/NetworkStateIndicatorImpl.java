package org.moire.ultrasonic.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.moire.ultrasonic.api.subsonic.NetworkStateIndicator;

class NetworkStateIndicatorImpl implements NetworkStateIndicator {
    private final ConnectivityManager connectivityManager;

    NetworkStateIndicatorImpl(final Context context) {
        connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isOnline() {
        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null &&
                info.isConnectedOrConnecting();
    }
}
