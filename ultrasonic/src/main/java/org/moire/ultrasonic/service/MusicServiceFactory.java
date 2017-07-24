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
package org.moire.ultrasonic.service;

import android.content.Context;
import android.content.SharedPreferences;

import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient;
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class MusicServiceFactory {
    private static MusicService REST_MUSIC_SERVICE = null;
    private static MusicService OFFLINE_MUSIC_SERVICE = null;

    public static MusicService getMusicService(Context context) {
        if (Util.isOffline(context)) {
            if (OFFLINE_MUSIC_SERVICE == null) {
                synchronized (MusicServiceFactory.class) {
                    if (OFFLINE_MUSIC_SERVICE == null) {
                        OFFLINE_MUSIC_SERVICE = new OfflineMusicService(createSubsonicApiClient(context));
                    }
                }
            }

            return OFFLINE_MUSIC_SERVICE;
        } else {
            if (REST_MUSIC_SERVICE == null) {
                synchronized (MusicServiceFactory.class) {
                    if (REST_MUSIC_SERVICE == null) {
                        REST_MUSIC_SERVICE = new CachedMusicService(new RESTMusicService(
                                createSubsonicApiClient(context)));
                    }
                }
            }

            return REST_MUSIC_SERVICE;
        }
    }

    private static SubsonicAPIClient createSubsonicApiClient(final Context context) {
        final SharedPreferences preferences = Util.getPreferences(context);
        int instance = preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
        String serverUrl = preferences.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
        String username = preferences.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
        String password = preferences.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);

        if (serverUrl == null ||
                username == null ||
                password == null) {
            throw new IllegalStateException("Server connection data is not available!");
        }

        return new SubsonicAPIClient(serverUrl, username, password,
                SubsonicAPIVersions.fromApiVersion(Constants.REST_PROTOCOL_VERSION),
                Constants.REST_CLIENT_ID, true);
    }
}
