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
package org.moire.ultrasonic.service

import android.content.Context
import org.koin.standalone.KoinComponent
import org.koin.standalone.get
import org.koin.standalone.release
import org.moire.ultrasonic.cache.Directories
import org.moire.ultrasonic.di.MUSIC_SERVICE_CONTEXT
import org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.moire.ultrasonic.util.Util

@Deprecated("Use DI way to get MusicService")
object MusicServiceFactory : KoinComponent {
    @JvmStatic
    fun getMusicService(context: Context): MusicService {
        return if (Util.isOffline(context)) {
            get(OFFLINE_MUSIC_SERVICE)
        } else {
            get(ONLINE_MUSIC_SERVICE)
        }
    }

    /**
     * Resets [MusicService] to initial state, so on next call to [.getMusicService]
     * it will return updated instance of it.
     */
    @JvmStatic
    fun resetMusicService() {
        release(MUSIC_SERVICE_CONTEXT)
    }

    @JvmStatic
    fun getServerId() = get<String>(name = "ServerID")

    @JvmStatic
    fun getDirectories() = get<Directories>()
}
